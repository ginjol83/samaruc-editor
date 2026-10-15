package com.retroeditor.service.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test del proveedor contra un servidor HTTP de mentira que habla el mismo protocolo que
 * {@code opencode serve}.
 *
 * Comprueba lo que los tests de traduccion pura no pueden ver: que el proveedor espera a estar
 * suscrito al stream antes de lanzar el prompt (si no, el primer evento de la respuesta se
 * pierde y el prompt se queda colgado hasta el timeout) y que no repite ni texto ni
 * herramientas cuando opencode reenvia cada parte con su estado completo.
 */
class OpenCodeProviderStreamTest {

    private static final String SESSION = "ses_test";

    @TempDir
    Path tempDir;

    private final List<AiStreamEvent> received = Collections.synchronizedList(new ArrayList<>());

    @Test
    void subscribesBeforePromptingAndStreamsEachFragmentOnce() throws Exception {
        FakeServer server = new FakeServer();
        server.listen();
        server.repliesWith("""
                data: {"type":"message.updated","properties":{"sessionID":"%s",\
                "info":{"id":"msg_user","role":"user","sessionID":"%s"}}}\n\n\
                data: {"type":"message.part.updated","properties":{"sessionID":"%s",\
                "part":{"id":"p0","messageID":"msg_user","sessionID":"%s","type":"text",\
                "text":"hola"}}}\n\n\
                data: {"type":"message.updated","properties":{"sessionID":"%s",\
                "info":{"id":"msg_bot","role":"assistant","sessionID":"%s"}}}\n\n\
                data: {"type":"message.part.updated","properties":{"sessionID":"%s",\
                "part":{"id":"p1","messageID":"msg_bot","sessionID":"%s","type":"text","text":"hola"}}}\n\n\
                data: {"type":"message.part.updated","properties":{"sessionID":"%s",\
                "part":{"id":"p1","messageID":"msg_bot","sessionID":"%s","type":"text","text":"hola mundo"}}}\n\n\
                data: {"type":"message.part.updated","properties":{"sessionID":"%s",\
                "part":{"id":"p2","sessionID":"%s","type":"tool","tool":"read",\
                "state":{"status":"pending","title":"main.c"}}}}\n\n\
                data: {"type":"message.part.updated","properties":{"sessionID":"%s",\
                "part":{"id":"p2","sessionID":"%s","type":"tool","tool":"read",\
                "state":{"status":"running","title":"main.c"}}}}\n\n\
                data: {"type":"session.idle","properties":{"sessionID":"%s"}}\n\n"""
                .formatted(SESSION, SESSION, SESSION, SESSION, SESSION, SESSION, SESSION, SESSION,
                        SESSION, SESSION, SESSION, SESSION, SESSION, SESSION, SESSION));
        OpenCodeProvider provider = new OpenCodeProvider(new FakeBridge(server.baseUri()));
        try {
            provider.start(new AiProvider.AiWorkspace(tempDir), received::add);
            String session = provider.createSession();

            provider.prompt(session, "hola");

            assertEquals(SESSION, session);
            assertTrue(server.promptArrivedAfterSubscription(),
                    "el promptAsync se lanzo sin stream: los primeros eventos de la respuesta se "
                        + "habrian perdido y el prompt se quedaria colgado hasta el timeout");
            assertEquals("hola mundo", text(),
                    "el texto acumulado de opencode debe llegar una sola vez, no repetido");

            List<AiStreamEvent.ToolStart> tools = received.stream()
                    .filter(AiStreamEvent.ToolStart.class::isInstance)
                    .map(AiStreamEvent.ToolStart.class::cast)
                    .toList();
            assertEquals(1, tools.size(),
                    "la herramienta pasa por pending y running: hay que anunciarla una vez");
            assertEquals("read", tools.get(0).tool());

            assertInstanceOf(AiStreamEvent.Done.class, received.get(received.size() - 1),
                    "session.idle debe cerrar el turno");
        } finally {
            provider.close();
            server.stop();
        }
    }

    @Test
    void reportsAnErrorFromTheServerAndUnblocksThePrompt() throws Exception {
        FakeServer server = new FakeServer();
        server.listen();
        server.repliesWith("""
                data: {"type":"session.error","properties":{"sessionID":"%s",\
                "error":{"name":"UnknownError","data":{"message":"sin credenciales"}}}}\n\n"""
                .formatted(SESSION));
        OpenCodeProvider provider = new OpenCodeProvider(new FakeBridge(server.baseUri()));
        try {
            provider.start(new AiProvider.AiWorkspace(tempDir), received::add);
            String session = provider.createSession();

            provider.prompt(session, "hola");

            assertEquals("sin credenciales",
                    assertInstanceOf(AiStreamEvent.Error.class, received.get(0)).message());
        } finally {
            provider.close();
            server.stop();
        }
    }

    @Test
    void ignoresEventsOfOtherSessions() throws Exception {
        FakeServer server = new FakeServer();
        server.listen();
        server.repliesWith("""
                data: {"type":"message.part.updated","properties":{\
                "part":{"id":"p9","sessionID":"sesion-ajena","type":"text","text":"no es mio"}}}\n\n\
                data: {"type":"session.idle","properties":{"sessionID":"%s"}}\n\n"""
                .formatted(SESSION));
        OpenCodeProvider provider = new OpenCodeProvider(new FakeBridge(server.baseUri()));
        try {
            provider.start(new AiProvider.AiWorkspace(tempDir), received::add);
            String session = provider.createSession();

            provider.prompt(session, "hola");

            assertTrue(received.stream().noneMatch(AiStreamEvent.TextDelta.class::isInstance),
                    "los eventos de otra sesion del mismo directorio no deben colarse en el panel");
        } finally {
            provider.close();
            server.stop();
        }
    }

    @Test
    void refusesToStartWhenTheProviderIsClosed() throws Exception {
        FakeServer server = new FakeServer();
        server.listen();
        OpenCodeProvider provider = new OpenCodeProvider(new FakeBridge(server.baseUri()));
        provider.close();

        try {
            provider.start(new AiProvider.AiWorkspace(tempDir), received::add);
            throw new AssertionError("un proveedor cerrado no debe volver a arrancar");
        } catch (AiProvider.AiException expected) {
            assertTrue(expected.getMessage().contains("cerrado"));
        } finally {
            server.stop();
        }
    }

    /** Texto concatenado de todos los fragmentos, tal como lo veria el usuario. */
    private String text() {
        StringBuilder builder = new StringBuilder();
        for (AiStreamEvent event : received) {
            if (event instanceof AiStreamEvent.TextDelta delta) {
                builder.append(delta.text());
            }
        }
        return builder.toString();
    }

    /**
     * Sustituye al proceso real: solo importa que el proveedor hable HTTP contra una URI
     * concreta, y el resto de metodos no se usan en estos tests.
     */
    private static final class FakeBridge extends OpenCodeServerBridge {

        private final HttpClient http = HttpClient.newHttpClient();
        private final URI base;

        FakeBridge(URI base) {
            super(AiSettings.defaults());
            this.base = base;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean start(Path workspace) {
            return true;
        }

        @Override
        public Optional<URI> baseUri() {
            return Optional.of(base);
        }

        @Override
        public String post(URI path, String jsonBody) throws AiProvider.AiException {
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(base.resolve(path))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new AiProvider.AiException("POST " + path + " devolvio " + response.statusCode());
                }
                return response.body();
            } catch (IOException e) {
                throw new AiProvider.AiException("fallo de red contra opencode: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProvider.AiException("interrumpido", e);
            }
        }

        @Override
        public InputStream openEventStream(URI path) throws AiProvider.AiException {
            try {
                HttpResponse<InputStream> response = http.send(
                        HttpRequest.newBuilder(base.resolve(path))
                                .header("Accept", "text/event-stream")
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new AiProvider.AiException("stream devolvio " + response.statusCode());
                }
                return response.body();
            } catch (IOException e) {
                throw new AiProvider.AiException("fallo de red contra opencode: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProvider.AiException("interrumpido", e);
            }
        }
    }

    /**
     * Servidor minimo con los tres endpoints que usa el proveedor.
     *
     * El stream lo escribe siempre el hilo que atiende {@code /event}: el JDK cierra el flujo
     * del exchange si se escribe desde otro hilo, asi que los eventos se encolan y se vuelcan
     * desde ahi.
     */
    private static final class FakeServer {

        private final HttpServer server;
        private final ExecutorService executor;
        private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>();
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private volatile String reply = "";
        private volatile boolean finished;
        private volatile boolean promptAfterSubscription;

        FakeServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // Sin executor propio el servidor atenderia las peticiones de una en una y el
            // stream abierto bloquearia para siempre al prompt.
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.createContext("/event", exchange -> {
                OutputStream out = exchange.getResponseBody();
                // Sin longitud declarada la respuesta es chunked: es un SSE que no termina.
                exchange.sendResponseHeaders(200, 0);
                subscribed.countDown();
                try {
                    while (!finished) {
                        String chunk = outbox.poll(100, TimeUnit.MILLISECONDS);
                        if (chunk != null) {
                            out.write(chunk.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.createContext("/session",
                    exchange -> respond(exchange, "{\"id\":\"" + SESSION + "\"}"));
            server.createContext("/session/" + SESSION + "/prompt_async", exchange -> {
                promptAfterSubscription = subscribed.getCount() == 0;
                outbox.add(reply);
                respond(exchange, "");
            });
        }

        void listen() {
            server.start();
        }

        URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        void stop() {
            finished = true;
            server.stop(0);
            executor.shutdownNow();
        }

        /** Eventos que se emitiran en el stream cuando llegue el prompt. */
        void repliesWith(String rawEvents) {
            reply = rawEvents;
        }

        boolean promptArrivedAfterSubscription() {
            return promptAfterSubscription;
        }

        private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
