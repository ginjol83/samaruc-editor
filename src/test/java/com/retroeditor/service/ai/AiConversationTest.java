package com.retroeditor.service.ai;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.retroeditor.controller.ai.AiChatMessage;
import com.retroeditor.service.ai.AiConversation;
import com.retroeditor.service.ai.AiStreamEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConversationTest {

    @Test
    void startsEmpty() {
        AiConversation conversation = new AiConversation();

        assertTrue(conversation.isEmpty());
        assertTrue(conversation.messages().isEmpty());
    }

    @Test
    void ignoresBlankMessages() {
        AiConversation conversation = new AiConversation();

        conversation.add(AiChatMessage.assistant("   "));
        conversation.add(null);

        assertTrue(conversation.isEmpty());
    }

    @Test
    void keepsInsertionOrder() {
        AiConversation conversation = new AiConversation();
        conversation.add(AiChatMessage.user("hola"));
        conversation.add(AiChatMessage.assistant("buenas"));

        List<AiChatMessage> messages = conversation.messages();
        assertEquals(2, messages.size());
        assertEquals(AiChatMessage.Role.USER, messages.get(0).role());
        assertEquals(AiChatMessage.Role.ASSISTANT, messages.get(1).role());
    }

    @Test
    void messagesListIsImmutable() {
        AiConversation conversation = new AiConversation();
        conversation.add(AiChatMessage.user("hola"));

        assertThrows(UnsupportedOperationException.class,
                () -> conversation.messages().add(AiChatMessage.user("otro")));
    }

    @Test
    void mergesConsecutiveTextDeltas() {
        AiConversation conversation = new AiConversation();

        conversation.appendText("Hola ");
        conversation.appendText("mundo");
        conversation.appendText("!");

        assertEquals(1, conversation.messages().size());
        assertEquals("Hola mundo!", conversation.messages().get(0).text());
    }

    @Test
    void opensNewMessageAfterANonAssistantMessage() {
        AiConversation conversation = new AiConversation();
        conversation.appendText("primera");
        conversation.add(AiChatMessage.user("interrumpo"));
        conversation.appendText("segunda");

        assertEquals(3, conversation.messages().size());
        assertEquals("primera", conversation.messages().get(0).text());
        assertEquals("segunda", conversation.messages().get(2).text());
    }

    @Test
    void ignoresEmptyTextDeltas() {
        AiConversation conversation = new AiConversation();

        conversation.appendText("");
        conversation.appendText(null);

        assertTrue(conversation.isEmpty());
    }

    @Test
    void acceptsTextDeltaEvent() {
        AiConversation conversation = new AiConversation();

        assertTrue(conversation.accept(AiStreamEvent.textDelta("hola"), null, null));

        assertEquals("hola", conversation.last(AiChatMessage.Role.ASSISTANT).orElseThrow().text());
    }

    @Test
    void acceptsToolEventWithTranslatedText() {
        AiConversation conversation = new AiConversation();

        boolean handled = conversation.accept(new AiStreamEvent.ToolStart("bash", "ls"),
                null, "Herramienta: bash");

        assertTrue(handled);
        assertEquals("Herramienta: bash", conversation.last(AiChatMessage.Role.TOOL).orElseThrow().text());
    }

    @Test
    void toolEventFallsBackToRawTitle() {
        AiConversation conversation = new AiConversation();

        conversation.accept(new AiStreamEvent.ToolStart("read", "main.c"), null, null);

        assertEquals("main.c", conversation.last(AiChatMessage.Role.TOOL).orElseThrow().text());
    }

    @Test
    void acceptsStatusEvent() {
        AiConversation conversation = new AiConversation();

        boolean handled = conversation.accept(AiStreamEvent.status("Arrancando..."), "Arrancando...", null);

        assertTrue(handled);
        assertEquals("Arrancando...", conversation.last(AiChatMessage.Role.STATUS).orElseThrow().text());
    }

    @Test
    void acceptsErrorEvent() {
        AiConversation conversation = new AiConversation();

        boolean handled = conversation.accept(new AiStreamEvent.Error("se cayo", false), null, null);

        assertTrue(handled);
        assertEquals("se cayo", conversation.last(AiChatMessage.Role.ERROR).orElseThrow().text());
    }

    @Test
    void doneEventIsNotRendered() {
        AiConversation conversation = new AiConversation();

        assertFalse(conversation.accept(new AiStreamEvent.Done(), null, null));
        assertTrue(conversation.isEmpty());
    }

    @Test
    void notifiesListenerOnEveryChange() {
        AtomicInteger notifications = new AtomicInteger();
        AiConversation conversation = new AiConversation(c -> notifications.incrementAndGet());

        conversation.add(AiChatMessage.user("hola"));
        conversation.appendText("respuesta");
        conversation.accept(AiStreamEvent.status("pensando"), "pensando", null);

        assertEquals(3, notifications.get());
    }

    @Test
    void clearEmptiesAndNotifies() {
        List<AiChatMessage> seen = new CopyOnWriteArrayList<>();
        AiConversation conversation = new AiConversation(c -> seen.addAll(c.messages()));
        conversation.add(AiChatMessage.user("hola"));

        conversation.clear();

        assertTrue(conversation.isEmpty());
        assertEquals(1, seen.size());
    }

    @Test
    void clearOnEmptyConversationDoesNotNotify() {
        AtomicInteger notifications = new AtomicInteger();
        AiConversation conversation = new AiConversation(c -> notifications.incrementAndGet());

        conversation.clear();

        assertEquals(0, notifications.get());
    }

    @Test
    void lastFindsEarlierRole() {
        AiConversation conversation = new AiConversation();
        conversation.add(AiChatMessage.user("primera"));
        conversation.add(AiChatMessage.user("segunda"));

        assertEquals("segunda", conversation.last(AiChatMessage.Role.USER).orElseThrow().text());
    }

    @Test
    void messageRequiresRole() {
        assertThrows(NullPointerException.class, () -> new AiChatMessage(null, "texto"));
    }

    @Test
    void messageNormalisesNullText() {
        assertEquals("", new AiChatMessage(AiChatMessage.Role.ASSISTANT, null).text());
    }
}