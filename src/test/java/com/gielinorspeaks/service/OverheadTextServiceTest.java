package com.gielinorspeaks.service;

import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.DialogueSource;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.OverheadTextChanged;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for OverheadTextService.
 * Focus: Ensuring NPC overhead text is properly detected and player overhead text is filtered out.
 */
public class OverheadTextServiceTest {
	private Client mockClient;
	private OverheadTextService service;
	private List<DialogueEvent> capturedDialogueEvents;

	@Before
	public void setUp() {
		mockClient = mock(Client.class);

		// Mock local player for player name replacement
		Player mockLocalPlayer = mock(Player.class);
		when(mockLocalPlayer.getName()).thenReturn("TestPlayer");
		when(mockClient.getLocalPlayer()).thenReturn(mockLocalPlayer);

		service = new OverheadTextService(mockClient);
		capturedDialogueEvents = new ArrayList<>();
		service.setDialogueCallback(capturedDialogueEvents::add);
	}

	// ===========================
	// Overhead Text Detection
	// ===========================

	@Test
	public void testOnOverheadTextChanged_detectsNpcOverheadText() {
		// Arrange
		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(5678);
		when(mockNpc.getName()).thenReturn("Goblin");

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn("Oi! Get away from me!");

		// Act
		service.onOverheadTextChanged(event);

		// Assert
		assertEquals("Should capture one overhead text event", 1, capturedDialogueEvents.size());
		DialogueEvent dialogueEvent = capturedDialogueEvents.get(0);
		assertEquals("NPC ID should match", 5678, dialogueEvent.getNpcId());
		assertEquals("NPC name should match", "Goblin", dialogueEvent.getNpcName());
		assertEquals("Dialogue text should match", "Oi! Get away from me!", dialogueEvent.getDialogueText());
		assertEquals("Source should be OVERHEAD_TEXT", DialogueSource.OVERHEAD_TEXT, dialogueEvent.getSource());
		assertNull("Animation ID should be null for overhead text", dialogueEvent.getAnimationId());
	}

	@Test
	public void testOnOverheadTextChanged_ignoresPlayerOverheadText() {
		// Arrange
		Player mockPlayer = mock(Player.class);

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockPlayer);
		when(event.getOverheadText()).thenReturn("Hello!");

		// Act
		service.onOverheadTextChanged(event);

		// Assert
		assertEquals("Should not capture player overhead text", 0, capturedDialogueEvents.size());
	}

	@Test
	public void testOnOverheadTextChanged_ignoresNullOverheadText() {
		// Arrange
		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn("Hans");

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn(null); // Null text

		// Act
		service.onOverheadTextChanged(event);

		// Assert
		assertEquals("Should not capture null overhead text", 0, capturedDialogueEvents.size());
	}

	@Test
	public void testOnOverheadTextChanged_ignoresEmptyOverheadText() {
		// Arrange
		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn("Hans");

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn(""); // Empty text

		// Act
		service.onOverheadTextChanged(event);

		// Assert
		assertEquals("Should not capture empty overhead text", 0, capturedDialogueEvents.size());
	}

	@Test
	public void testOnOverheadTextChanged_handlesNullNpcName() {
		// Arrange
		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn(null); // Null name

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn("Hello!");

		// Act
		service.onOverheadTextChanged(event);

		// Assert
		assertEquals("Should use 'Unknown' for null name", "Unknown", capturedDialogueEvents.get(0).getNpcName());
	}

	@Test
	public void testOnOverheadTextChanged_handlesMultipleNpcs() {
		// Arrange
		NPC npc1 = mock(NPC.class);
		when(npc1.getId()).thenReturn(1111);
		when(npc1.getName()).thenReturn("Goblin 1");

		NPC npc2 = mock(NPC.class);
		when(npc2.getId()).thenReturn(2222);
		when(npc2.getName()).thenReturn("Goblin 2");

		OverheadTextChanged event1 = mock(OverheadTextChanged.class);
		when(event1.getActor()).thenReturn(npc1);
		when(event1.getOverheadText()).thenReturn("Arg!");

		OverheadTextChanged event2 = mock(OverheadTextChanged.class);
		when(event2.getActor()).thenReturn(npc2);
		when(event2.getOverheadText()).thenReturn("Grrr!");

		// Act
		service.onOverheadTextChanged(event1);
		service.onOverheadTextChanged(event2);

		// Assert
		assertEquals("Should capture both overhead text events", 2, capturedDialogueEvents.size());
		assertEquals("First NPC text", "Arg!", capturedDialogueEvents.get(0).getDialogueText());
		assertEquals("First NPC ID", 1111, capturedDialogueEvents.get(0).getNpcId());
		assertEquals("Second NPC text", "Grrr!", capturedDialogueEvents.get(1).getDialogueText());
		assertEquals("Second NPC ID", 2222, capturedDialogueEvents.get(1).getNpcId());
	}

	// ===========================
	// Callback Null Safety Tests
	// ===========================

	@Test
	public void testOnOverheadTextChanged_handlesNullCallback() {
		// Arrange
		service.setDialogueCallback(null); // No callback

		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn("Hans");

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn("Hello!");

		// Act - Should not throw
		service.onOverheadTextChanged(event);

		// Assert - No assertion needed, just shouldn't crash
	}

	@Test
	public void testOnOverheadTextChanged_preservesOriginalText() {
		// Arrange - Overhead text is not cleaned (unlike dialogue box text)
		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn("Hans");

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn("Text with   multiple   spaces");

		// Act
		service.onOverheadTextChanged(event);

		// Assert - Overhead text should NOT be cleaned (no HTML in overhead text)
		assertEquals("Overhead text should be preserved as-is",
			"Text with   multiple   spaces",
			capturedDialogueEvents.get(0).getDialogueText());
	}

	@Test
	public void testOnOverheadTextChanged_capturesPlayerName() {
		// Arrange
		NPC mockNpc = mock(NPC.class);
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn("Guard");

		OverheadTextChanged event = mock(OverheadTextChanged.class);
		when(event.getActor()).thenReturn(mockNpc);
		when(event.getOverheadText()).thenReturn("Stop right there, TestPlayer!");

		// Act
		service.onOverheadTextChanged(event);

		// Assert - Player name should be captured for replacement later
		assertEquals("Should capture one event", 1, capturedDialogueEvents.size());
		DialogueEvent dialogueEvent = capturedDialogueEvents.get(0);
		assertEquals("Player name should be captured", "TestPlayer", dialogueEvent.getPlayerName());

		// Verify that toSpeakRequest() replaces player name with "Adventurer"
		assertEquals("Player name should be replaced in API request",
			"Stop right there, Adventurer!",
			dialogueEvent.toSpeakRequest().getText());
	}
}