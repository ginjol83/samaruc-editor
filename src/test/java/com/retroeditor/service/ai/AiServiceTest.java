package com.retroeditor.service.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.service.ai.AiService;
import com.retroeditor.service.ai.AiStreamEvent;
import com.retroeditor.service.ai.AiProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceTest {

    @TempDir
    Path tempDir;

    /** Proveedor falso: no toca procesos ni red, solo permite observar el contrato. */
    private static final class FakeProvider implements AiProvider {
        private final boolean available;
        private final List<String> prompts = new CopyOnWriteArrayList<>();
        private final List<String> aborted = new CopyOnWriteArrayList<>();
        private final List<AiProvider.AiWorkspace> workspaces = new CopyOnWriteArrayList<>();
        private final AtomicInteger sessions = new AtomicInteger();
        private AiEventSink sink;
        private final AtomicInteger completed = new AtomicInteger();
        private boolean closed;
        private String promptFailure;
        private long promptDelayMillis;

        FakeProvider() {
            this(true);
        }

        FakeProvider(boolean available) {
            this.available = available;
        }

        FakeProvider(AiEventSink sink) {
            this.available = true;
            this.sink = sink;
        }

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String unavailableReason() {
            return available ? null : "fake no disponible";
        }

        @Override
        public void start(AiProvider.AiWorkspace workspace, AiEventSink eventSink) {
            workspaces.add(workspace);
        }

        @Override
        public String createSession() {
            return "sesion-" + sessions.incrementAndGet();
        }

        @Override
        public void prompt(String sessionId, String prompt) throws AiException {
            prompts.add(sessionId + "|" + prompt);
            if (promptDelayMillis > 0) {
                try {
                    Thread.sleep(promptDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                if (promptFailure != null) {
                    throw new AiException(promptFailure);
                }
                if (sink != null) {
                    sink.accept(AiStreamEvent.textDelta("hola"));
                    sink.accept(new AiStreamEvent.Done());
                }
            } finally {
                completed.incrementAndGet();
            }
        }

        @Override
        public void abort(String sessionId) {
            aborted.add(sessionId);
        }

        boolean awaitAborts(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (aborted.size() < count && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            return aborted.size() >= count;
        }

        @Override
        public void close() {
            closed = true;
        }

        /** Espera a que se hayan completado {@code count} prompts. */
        boolean awaitPrompts(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (completed.get() < count && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            return completed.get() >= count;
        }
    }

    private static List<AiStreamEvent> capture() {
        List<AiStreamEvent> events = new CopyOnWriteArrayList<>();
        return events;
    }

    private static AiService service(List<AiStreamEvent> events) {
        return new AiService(Runnable::run, events::add);
    }

    /**
     * Espera a que el prompt termine y el servicio vuelva a estar libre. Hace falta en
     * los casos con dos envios: el contador del proveedor baja antes de que el servicio
     * libere el estado, y un envio en curso debe rechazarse.
     */
    private static void awaitIdle(AiService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (service.isBusy() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void sendRunsThroughTheProvider() throws Exception {
        List<AiStreamEvent> events = capture();
        FakeProvider provider = new FakeProvider();
        AiService service = service(events);
        service.useProvider(provider);

        assertTrue(service.send("hola agente"));

        assertTrue(provider.awaitPrompts(1));
        assertEquals(1, provider.prompts.size());
        assertEquals("sesion-1|hola agente", provider.prompts.get(0));
        assertEquals(AiService.State.IDLE, service.state());
        service.close();
    }

    @Test
    void eventsAreForwardedToTheUi() throws Exception {
        List<AiStreamEvent> events = capture();
        FakeProvider provider = new FakeProvider(events::add);
        AiService service = service(events);
        service.useProvider(provider);

        service.send("hola");
        assertTrue(provider.awaitPrompts(1));

        assertTrue(events.stream().anyMatch(e -> e instanceof AiStreamEvent.TextDelta));
        assertTrue(events.stream().anyMatch(e -> e instanceof AiStreamEvent.Done));
        service.close();
    }

    @Test
    void reusesSessionForTheSameWorkspace() throws Exception {
        FakeProvider provider = new FakeProvider();
        AiService service = service(capture());
        service.useProvider(provider);
        service.useWorkspace(tempDir);

        service.send("primero");
        assertTrue(provider.awaitPrompts(1));
        awaitIdle(service);
        service.send("segundo");
        assertTrue(provider.awaitPrompts(2));

        assertEquals(1, provider.sessions.get());
        assertEquals(2, provider.prompts.size());
        assertTrue(provider.prompts.get(0).startsWith("sesion-1|"));
        assertTrue(provider.prompts.get(1).startsWith("sesion-1|"));
        service.close();
    }

    @Test
    void createsNewSessionWhenWorkspaceChanges() throws Exception {
        FakeProvider provider = new FakeProvider();
        AiService service = service(capture());
        service.useProvider(provider);
        service.useWorkspace(tempDir.resolve("proyecto-a"));

        service.send("en el proyecto A");
        assertTrue(provider.awaitPrompts(1));
        awaitIdle(service);
        service.useWorkspace(tempDir.resolve("proyecto-b"));
        service.send("en el proyecto B");
        assertTrue(provider.awaitPrompts(2));

        assertEquals(2, provider.sessions.get());
        assertTrue(provider.aborted.contains("sesion-1"));
        assertEquals(2, provider.workspaces.size());
        assertNotEquals(provider.workspaces.get(0), provider.workspaces.get(1));
        service.close();
    }

    @Test
    void resetSessionDropsTheSession() throws Exception {
        FakeProvider provider = new FakeProvider();
        AiService service = service(capture());
        service.useProvider(provider);
        service.useWorkspace(tempDir);
        service.send("uno");
        assertTrue(provider.awaitPrompts(1));
        awaitIdle(service);

        service.resetSession();
        service.send("dos");
        assertTrue(provider.awaitPrompts(2));

        assertEquals(2, provider.sessions.get());
        service.close();
    }

    @Test
    void refusesToSendWithoutProvider() {
        List<AiStreamEvent> events = capture();
        AiService service = service(events);

        assertFalse(service.send("hola"));
        assertTrue(events.stream().anyMatch(e -> e instanceof AiStreamEvent.Error));
        assertEquals(AiService.State.UNAVAILABLE, service.state());
        assertTrue(service.lastError().isPresent());
        service.close();
    }

    @Test
    void refusesToSendWhenProviderIsUnavailable() {
        List<AiStreamEvent> events = capture();
        AiService service = service(events);
        service.useProvider(new FakeProvider(false));

        assertFalse(service.send("hola"));
        assertEquals(AiService.State.UNAVAILABLE, service.state());
        service.close();
    }

    @Test
    void ignoresBlankPrompts() {
        AiService service = service(capture());
        service.useProvider(new FakeProvider());

        assertFalse(service.send("   "));
        assertFalse(service.send(null));
        service.close();
    }

    @Test
    void providerFailureIsReportedAsErrorEventAndLeavesPanelUsable() throws Exception {
        List<AiStreamEvent> events = capture();
        FakeProvider provider = new FakeProvider();
        provider.promptFailure = "sesion no encontrada";
        AiService service = service(events);
        service.useProvider(provider);

        service.send("hola");
        assertTrue(provider.awaitPrompts(1));

        AiStreamEvent.Error error = events.stream()
                .filter(AiStreamEvent.Error.class::isInstance)
                .map(AiStreamEvent.Error.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("sesion no encontrada", error.message());
        assertTrue(service.lastError().isPresent());
        // Un fallo de una peticion no deja el panel bloqueado para siempre.
        assertEquals(AiService.State.IDLE, service.state());
        service.close();
    }

    @Test
    void busyServiceRejectsSecondPrompt() throws Exception {
        FakeProvider provider = new FakeProvider();
        provider.promptDelayMillis = 400;
        AiService service = service(capture());
        service.useProvider(provider);

        assertTrue(service.send("primero"));
        assertFalse(service.send("segundo"), "no debe aceptar dos peticiones a la vez");
        assertTrue(provider.awaitPrompts(1));
        assertEquals(1, provider.prompts.size());
        service.close();
    }

    @Test
    void cancelAbortsTheRunningSession() throws Exception {
        FakeProvider provider = new FakeProvider();
        provider.promptDelayMillis = 300;
        AiService service = service(capture());
        service.useProvider(provider);
        service.useWorkspace(tempDir);
        service.send("lento");
        provider.awaitPrompts(1);

        service.cancel();
        // cancel() se encola en el mismo executor que el prompt: hay que esperar a que
        // corra antes de cerrar, porque close() descarta lo pendiente.
        assertTrue(provider.awaitAborts(1));
        service.close();

        assertEquals(1, provider.aborted.size());
        assertEquals("sesion-1", provider.aborted.get(0));
    }

    @Test
    void cancelWithoutSessionJustGoesIdle() {
        FakeProvider provider = new FakeProvider();
        AiService service = service(capture());
        service.useProvider(provider);

        service.cancel();

        assertEquals(AiService.State.IDLE, service.state());
        assertTrue(provider.aborted.isEmpty());
        service.close();
    }

    @Test
    void closeIsIdempotentAndClosesProvider() throws Exception {
        FakeProvider provider = new FakeProvider();
        AiService service = service(capture());
        service.useProvider(provider);
        service.send("hola");
        assertTrue(provider.awaitPrompts(1));

        service.close();
        service.close();

        assertTrue(provider.closed);
        assertEquals(AiService.State.CLOSED, service.state());
        assertFalse(service.send("otra"), "no debe aceptar peticiones tras cerrar");
    }

    @Test
    void usesConfiguredDefaultsWhenNoWorkspaceSet() throws Exception {
        FakeProvider provider = new FakeProvider();
        AiService service = service(capture());
        service.useProvider(provider);

        service.send("hola");
        assertTrue(provider.awaitPrompts(1));

        assertEquals(1, provider.workspaces.size());
        assertTrue(provider.workspaces.get(0).directory().isAbsolute());
        service.close();
    }
}
