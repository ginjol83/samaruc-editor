package com.retroeditor.service.ai;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la traduccion de eventos de opencode a {@link AiStreamEvent}. Es la parte con reglas
 * del proveedor y la unica que no necesita ni proceso ni red.
 */
class OpenCodeProviderTest {

    private final OpenCodeProvider provider = new OpenCodeProvider(null);

    /** Texto de un TextDelta, para no repetir el cast en cada asercion. */
    private static String textOf(AiStreamEvent event) {
        return assertInstanceOf(AiStreamEvent.TextDelta.class, event).text();
    }

    private static String textPart(String sessionId, String text, String delta) {
        return """
                {"type":"message.part.updated","properties":{\
                "part":{"id":"p1","sessionID":"%s","messageID":"m1","type":"text","text":"%s"},\
                "delta":"%s"}}"""
                .formatted(sessionId, text, delta);
    }

    @Test
    void translatesTextPartIntoTextDelta() {
        List<AiStreamEvent> events = provider.translate(textPart("sesion-1", "hola mundo", ""));

        assertEquals(1, events.size());
        assertEquals("hola mundo", textOf(events.get(0)));
    }

    @Test
    void prefersDeltaOverFullText() {
        List<AiStreamEvent> events = provider.translate(textPart("sesion-1", "hola mundo", " mundo"));

        assertEquals(" mundo", textOf(events.get(0)),
                "el delta evita repintar el parrafo entero en cada fragmento");
    }

    @Test
    void ignoresEmptyTextPart() {
        assertTrue(provider.translate(textPart("sesion-1", "", "")).isEmpty());
    }

    @Test
    void translatesRunningToolIntoToolStart() {
        String json = """
                {"type":"message.part.updated","properties":{\
                "part":{"id":"p2","sessionID":"sesion-1","messageID":"m1","type":"tool","tool":"read",\
                "state":{"status":"running","title":"main.c","input":{},"time":{"start":1}}}}}""";

        AiStreamEvent.ToolStart tool = assertInstanceOf(AiStreamEvent.ToolStart.class,
                provider.translate(json).get(0));

        assertEquals("read", tool.tool());
        assertEquals("main.c", tool.title());
    }

    @Test
    void ignoresFinishedTool() {
        String json = """
                {"type":"message.part.updated","properties":{\
                "part":{"id":"p2","sessionID":"sesion-1","type":"tool","tool":"read",\
                "state":{"status":"completed","output":"x"}}}}""";

        assertTrue(provider.translate(json).isEmpty(),
                "una herramienta terminada no es una herramienta en curso");
    }

    @Test
    void translatesSessionErrorIntoErrorEvent() {
        String json = """
                {"type":"session.error","properties":{"sessionID":"sesion-1",\
                "error":{"name":"UnknownError","data":{"message":"sin credenciales"}}}}""";

        AiStreamEvent.Error error = assertInstanceOf(AiStreamEvent.Error.class,
                provider.translate(json).get(0));

        assertEquals("sin credenciales", error.message());
    }

    @Test
    void translatesIdleIntoDone() {
        List<AiStreamEvent> events = provider.translate(
                "{\"type\":\"session.idle\",\"properties\":{\"sessionID\":\"sesion-1\"}}");

        assertEquals(1, events.size());
        assertInstanceOf(AiStreamEvent.Done.class, events.get(0));
    }

    @Test
    void translatesPermissionRequest() {
        String json = """
                {"type":"permission.asked","properties":{"id":"perm-1","sessionID":"sesion-1",\
                "permission":"bash","patterns":["rm -rf /"]}}""";

        AiStreamEvent.PermissionRequest req = assertInstanceOf(AiStreamEvent.PermissionRequest.class,
                provider.translate(json).get(0));

        assertEquals("perm-1", req.requestId());
        assertEquals("bash", req.permission());
        assertEquals(List.of("rm -rf /"), req.patterns());
    }

    @Test
    void translatesPermissionV2Request() {
        String json = """
                {"type":"permission.v2.asked","properties":{"id":"perm-2","sessionID":"sesion-1",\
                "action":"edit","patterns":["src/foo.java"]}}""";

        AiStreamEvent.PermissionRequest req = assertInstanceOf(AiStreamEvent.PermissionRequest.class,
                provider.translate(json).get(0));

        assertEquals("perm-2", req.requestId());
        assertEquals("edit", req.permission());
        assertEquals(List.of("src/foo.java"), req.patterns());
    }

    @Test
    void deduplicatesPermissionRequests() {
        String json = """
                {"type":"permission.asked","properties":{"id":"perm-1","sessionID":"sesion-1",\
                "permission":"bash","patterns":["rm"]}}""";

        OpenCodeProvider.TurnState state = new OpenCodeProvider.TurnState();
        List<AiStreamEvent> first = provider.translate(json, state);
        List<AiStreamEvent> second = provider.translate(json, state);

        assertEquals(1, first.size());
        assertEquals(0, second.size());
    }

    @Test
    void reportsRetryStatus() {
        String json = """
                {"type":"session.status","properties":{"sessionID":"sesion-1",\
                "status":{"type":"retry","attempt":2,"message":"rate limit","next":1000}}}""";

        AiStreamEvent.Status status = assertInstanceOf(AiStreamEvent.Status.class,
                provider.translate(json).get(0));

        assertTrue(status.message().contains("rate limit"));
    }

    @Test
    void ignoresEventsWithoutChatContent() {
        assertTrue(provider.translate("{\"type\":\"server.connected\",\"properties\":{}}").isEmpty());
        assertTrue(provider.translate("{\"type\":\"todo.updated\",\"properties\":{\"sessionID\":\"s\"}}").isEmpty());
    }

    @Test
    void ignoresMalformedPayload() {
        assertTrue(provider.translate("esto no es json").isEmpty());
        assertTrue(provider.translate("").isEmpty());
    }

    @Test
    void exposesOpaqueProviderId() {
        assertEquals("opencode", provider.id());
    }

    @Test
    void reportsUnavailableWithoutBridge() {
        assertTrue(!provider.isAvailable());
        assertTrue(provider.unavailableReason() != null);
    }
}