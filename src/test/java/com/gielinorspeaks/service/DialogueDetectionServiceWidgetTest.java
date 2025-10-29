package com.gielinorspeaks.service;

import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.DialogueSource;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DialogueDetectionService widget handling.
 * Tests onWidgetLoaded and onWidgetClosed event handlers.
 */
public class DialogueDetectionServiceWidgetTest extends DialogueDetectionServiceTestBase {
	// ===========================
	// onWidgetLoaded Tests
	// ===========================

	@Test
	public void testOnWidgetLoaded_detectsDialogueFromChatLeftWidget() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("<col=000080>Hello, adventurer!</col>");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should capture one dialogue event", 1, capturedDialogueEvents.size());
		DialogueEvent dialogueEvent = capturedDialogueEvents.get(0);
		assertEquals("NPC ID should match", 1234, dialogueEvent.getNpcId());
		assertEquals("NPC name should match", "Hans", dialogueEvent.getNpcName());
		assertEquals("Dialogue text should be cleaned", "Hello, adventurer!", dialogueEvent.getDialogueText());
		assertEquals("Source should be DIALOGUE_BOX", DialogueSource.DIALOGUE_BOX, dialogueEvent.getSource());
	}

	@Test
	public void testOnWidgetLoaded_detectsDialogueFromChatRightWidget() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_RIGHT);

		when(mockClient.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("Welcome to RuneScape!");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should capture one dialogue event", 1, capturedDialogueEvents.size());
		assertEquals("Dialogue text should match", "Welcome to RuneScape!", capturedDialogueEvents.get(0).getDialogueText());
	}

	@Test
	public void testOnWidgetLoaded_ignoresHiddenWidget() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(true); // Widget is hidden

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should not capture any events", 0, capturedDialogueEvents.size());
	}

	@Test
	public void testOnWidgetLoaded_ignoresNullWidget() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(null); // Widget doesn't exist

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should not capture any events", 0, capturedDialogueEvents.size());
	}

	@Test
	public void testOnWidgetLoaded_deduplicatesSameDialogueText() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("Hello!");

		// Act - Fire the same dialogue twice
		service.onWidgetLoaded(event);
		service.onWidgetLoaded(event);

		// Assert - Should only capture once
		assertEquals("Should only capture one event due to deduplication", 1, capturedDialogueEvents.size());
	}

	@Test
	public void testOnWidgetLoaded_allowsDifferentDialogueText() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);

		// Act - Fire two different dialogues
		when(mockDialogWidget.getText()).thenReturn("Hello!");
		service.onWidgetLoaded(event);

		when(mockDialogWidget.getText()).thenReturn("Goodbye!");
		service.onWidgetLoaded(event);

		// Assert - Should capture both
		assertEquals("Should capture two different events", 2, capturedDialogueEvents.size());
		assertEquals("First dialogue", "Hello!", capturedDialogueEvents.get(0).getDialogueText());
		assertEquals("Second dialogue", "Goodbye!", capturedDialogueEvents.get(1).getDialogueText());
	}

	@Test
	public void testOnWidgetLoaded_cleansHtmlTags() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("<col=ff0000>Red text</col> and <b>bold</b>   multiple   spaces");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("HTML should be cleaned and whitespace normalized",
			"Red text and bold multiple spaces",
			capturedDialogueEvents.get(0).getDialogueText());
	}

	@Test
	public void testOnWidgetLoaded_firesDialogueEndOnChatMenu() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHATMENU);

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should fire dialogue end callback", 1, capturedDialogueEndEvents.size());
		assertEquals("NPC ID should match", Integer.valueOf(1234), capturedDialogueEndEvents.get(0));
	}

	@Test
	public void testOnWidgetLoaded_ignoresOtherInterfaceTypes() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(999); // Some other interface

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should not capture any events", 0, capturedDialogueEvents.size());
		assertEquals("Should not fire dialogue end", 0, capturedDialogueEndEvents.size());
	}

	@Test
	public void testOnWidgetLoaded_handlesNullNpcNameGracefully() {
		// Arrange
		setupNpcInteraction();
		when(mockNpc.getName()).thenReturn(null); // Null name

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("Hello!");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should use 'Unknown' for null name", "Unknown", capturedDialogueEvents.get(0).getNpcName());
	}

	@Test
	public void testOnWidgetLoaded_skipsPlayerDialogue() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		// Set up name widget to show player's name (indicating player dialogue)
		Widget nameWidget = mock(Widget.class);
		when(mockClient.getWidget(InterfaceID.ChatLeft.NAME)).thenReturn(nameWidget);
		when(nameWidget.isHidden()).thenReturn(false);
		when(nameWidget.getText()).thenReturn("PlayerName");
		when(mockPlayer.getName()).thenReturn("PlayerName"); // Same as dialogue name

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("This is what I said!");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should not capture player dialogue", 0, capturedDialogueEvents.size());
	}

	@Test
	public void testOnWidgetLoaded_allowsNpcDialogueWithDifferentName() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		// Set up name widget to show NPC's name (different from player)
		Widget nameWidget = mock(Widget.class);
		when(mockClient.getWidget(InterfaceID.ChatLeft.NAME)).thenReturn(nameWidget);
		when(nameWidget.isHidden()).thenReturn(false);
		when(nameWidget.getText()).thenReturn("Hans"); // NPC name
		when(mockPlayer.getName()).thenReturn("PlayerName"); // Different from dialogue name

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("Hello adventurer!");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should capture NPC dialogue", 1, capturedDialogueEvents.size());
		assertEquals("Dialogue text should match", "Hello adventurer!", capturedDialogueEvents.get(0).getDialogueText());
	}

	// ===========================
	// onWidgetClosed Tests
	// ===========================

	@Test
	public void testOnWidgetClosed_firesDialogueEndOnChatLeft() {
		// Arrange
		setupNpcInteraction();

		WidgetClosed event = mock(WidgetClosed.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		// Act
		service.onWidgetClosed(event);

		// Assert
		assertEquals("Should fire dialogue end callback", 1, capturedDialogueEndEvents.size());
		assertEquals("NPC ID should match", Integer.valueOf(1234), capturedDialogueEndEvents.get(0));
	}

	@Test
	public void testOnWidgetClosed_firesDialogueEndOnChatRight() {
		// Arrange
		setupNpcInteraction();

		WidgetClosed event = mock(WidgetClosed.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_RIGHT);

		// Act
		service.onWidgetClosed(event);

		// Assert
		assertEquals("Should fire dialogue end callback", 1, capturedDialogueEndEvents.size());
	}

	@Test
	public void testOnWidgetClosed_ignoresOtherInterfaceTypes() {
		// Arrange
		setupNpcInteraction();

		WidgetClosed event = mock(WidgetClosed.class);
		when(event.getGroupId()).thenReturn(999); // Some other interface

		// Act
		service.onWidgetClosed(event);

		// Assert
		assertEquals("Should not fire dialogue end", 0, capturedDialogueEndEvents.size());
	}

	@Test
	public void testOnWidgetClosed_resetsDialogueState() {
		// Arrange
		setupNpcInteraction();

		// First, trigger dialogue detection
		WidgetLoaded loadEvent = mock(WidgetLoaded.class);
		when(loadEvent.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);
		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("Hello!");
		service.onWidgetLoaded(loadEvent);

		capturedDialogueEvents.clear();

		// Close the widget
		WidgetClosed closeEvent = mock(WidgetClosed.class);
		when(closeEvent.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);
		service.onWidgetClosed(closeEvent);

		// Act - Send same dialogue again (simulating new dialogue window)
		service.onWidgetLoaded(loadEvent);

		// Assert - Should fire again because state was reset
		assertEquals("Should fire dialogue event after reset", 1, capturedDialogueEvents.size());
	}

	@Test
	public void testOnWidgetLoaded_capturesPlayerNameForReplacement() {
		// Arrange
		setupNpcInteraction();

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.CHAT_LEFT);

		when(mockClient.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(mockDialogWidget);
		when(mockDialogWidget.isHidden()).thenReturn(false);
		when(mockDialogWidget.getText()).thenReturn("Greetings, TestPlayer! Welcome to Lumbridge.");

		// Act
		service.onWidgetLoaded(event);

		// Assert
		assertEquals("Should capture one dialogue event", 1, capturedDialogueEvents.size());
		DialogueEvent dialogueEvent = capturedDialogueEvents.get(0);

		// Verify player name was captured
		assertEquals("Player name should be captured", "TestPlayer", dialogueEvent.getPlayerName());

		// Verify original text is preserved in the event
		assertEquals("Original dialogue text should be preserved",
			"Greetings, TestPlayer! Welcome to Lumbridge.",
			dialogueEvent.getDialogueText());

		// Verify that toSpeakRequest() replaces player name with "Adventurer"
		assertEquals("Player name should be replaced with Adventurer in API request",
			"Greetings, Adventurer! Welcome to Lumbridge.",
			dialogueEvent.toSpeakRequest().getText());
	}
}
