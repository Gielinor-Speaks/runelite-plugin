package com.gielinorspeaks.service;

import com.gielinorspeaks.model.DialogueEvent;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Base class for DialogueDetectionService tests.
 * Provides common setup, mocks, and helper methods.
 */
public abstract class DialogueDetectionServiceTestBase {
	protected DialogueDetectionService service;
	protected Client mockClient;
	protected ClientThread mockClientThread;
	protected Player mockPlayer;
	protected NPC mockNpc;
	protected Widget mockDialogWidget;
	protected Widget mockHeadWidget;

	protected List<DialogueEvent> capturedDialogueEvents;
	protected List<Integer> capturedDialogueEndEvents;

	@Before
	public void setUp() {
		mockClient = mock(Client.class);
		mockClientThread = mock(ClientThread.class);
		mockPlayer = mock(Player.class);
		mockNpc = mock(NPC.class);
		mockDialogWidget = mock(Widget.class);
		mockHeadWidget = mock(Widget.class);

		service = new DialogueDetectionService(mockClient, mockClientThread);

		capturedDialogueEvents = new ArrayList<>();
		capturedDialogueEndEvents = new ArrayList<>();

		// Set up callbacks to capture events
		service.setDialogueCallback(capturedDialogueEvents::add);
		service.setDialogueEndCallback(capturedDialogueEndEvents::add);

		// Default NPC setup
		when(mockNpc.getId()).thenReturn(1234);
		when(mockNpc.getName()).thenReturn("Hans");
		when(mockNpc.getIndex()).thenReturn(42);
		when(mockNpc.getAnimation()).thenReturn(-1);

		// Default player setup
		when(mockPlayer.getName()).thenReturn("TestPlayer");
		when(mockClient.getLocalPlayer()).thenReturn(mockPlayer);

		// Mock clientThread.invokeLater to execute immediately for testing
		doAnswer(invocation -> {
			Runnable runnable = invocation.getArgument(0);
			runnable.run();
			return null;
		}).when(mockClientThread).invokeLater(any(Runnable.class));
	}

	/**
	 * Sets up a basic NPC interaction scenario.
	 * Call this in tests that need an NPC to be cached.
	 */
	protected void setupNpcInteraction() {
		InteractingChanged event = mock(InteractingChanged.class);
		when(event.getSource()).thenReturn(mockPlayer);
		when(mockPlayer.getInteracting()).thenReturn(mockNpc);
		service.onInteractingChanged(event);
	}
}