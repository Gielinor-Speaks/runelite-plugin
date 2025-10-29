package com.gielinorspeaks.service;

import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.DialogueSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Consumer;

/**
 * Detects NPC dialogue from dialogue box interfaces using multiple callbacks.
 * ===
 * 1. onInteractingChanged: Captures NPC reference immediately when player clicks NPC
 * 2. onWidgetLoaded: Instant detection when dialogue appears (event-driven)
 * 3. onWidgetClosed: Cleanup and notification when dialogue ends
 * ===
 * State tracking: Prevents duplicate events when dialogue doesn't change
 * ====
 * This combines the efficiency of event-driven (avoids overpolling) with reliability
 * of state tracking (handles edge cases like widget reloads).
 * ===
 * NOTE ON THREAD SAFETY: All state is accessed only on the client thread via @Subscribe methods
 * and ClientThread.invokeLater().
 * ===
 * DESIGN DECISIONS:
 * - No polling: WidgetLoaded fires immediately when new dialogue appears
 * - invokeLater(): Ensures widget text is fully populated before reading
 * - cachedInteractingNpc: Captured early, since Player.getInteracting() becomes null after the interaction event.
 * - Deduplication: Tracks last text to avoid firing duplicate events
 */
@Slf4j
@Singleton
public class DialogueDetectionService {
	private final Client client;
	private final ClientThread clientThread;

    /**
     * Callback to be invoked when NPC dialogue is detected.
     * Called when NPC dialogue is displayed when a player interacts
     * with an NPC.
     */
    @Setter
	private Consumer<DialogueEvent> dialogueCallback;

	/**
	 * Callback to be invoked when NPC dialogue ends.
	 * Called when player options appear or dialogue window closes.
	 * Receives the NPC ID that was speaking.
	 */
	@Setter
	private Consumer<Integer> dialogueEndCallback;

	// State tracking
	// Note: These fields are only accessed on the client thread
	private String lastDialogueText = "";

	/**
	 * Cached NPC captured immediately when player interacts.
	 * Player.getInteracting() only works briefly, so we cache it early.
	 * Persists across dialogue advances until a new interaction starts.
	 */
	private NPC cachedInteractingNpc = null;

	@Inject
	public DialogueDetectionService(Client client, ClientThread clientThread) {
		this.client = client;
		this.clientThread = clientThread;
	}

	/**
	 * Captures NPC reference immediately when player starts interacting.
	 * This is critical because Player.getInteracting() is only non-null briefly.
	 */
	@Subscribe
	public void onInteractingChanged(InteractingChanged event) {
		// Check if the local player changed their interaction target
		if (event.getSource() == client.getLocalPlayer()) {
            // Capture the current actor that the player is interacting with
			Actor target = client.getLocalPlayer().getInteracting();
			if (target instanceof NPC) {
				NPC eventNPC = (NPC) target;
				// Only update if it's a new NPC (or first interaction)
                // Checks to make sure this entity is the same by inspecting the: the index position of this NPC in
                // the clients cached NPC array.
				if (cachedInteractingNpc == null || cachedInteractingNpc.getIndex() != eventNPC.getIndex()) {
					cachedInteractingNpc = eventNPC;
					resetDialogueState();
                    log.debug("Interaction started with {} (ID: {})", eventNPC.getName(), eventNPC.getId());
				}
			}
		}
	}

	/**
	 * Event-driven dialogue detection when dialogue widget loads.
	 * Uses invokeLater() to ensure widget text is fully populated.
	 * Fires immediately when dialogue appears (no polling delay).
	 * ===
	 * Also detects when chat menu appear, signaling dialogue end.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		// Detect chat menu appearing (dialogue ended, player's turn)
		if (event.getGroupId() == InterfaceID.CHATMENU) {
			fireDialogueEndCallback("Chat menu appeared");
			return;
		}

		// Only handle NPC dialogue interfaces (CHAT_LEFT or CHAT_RIGHT)
		if (!isChatDialogueInterface(event.getGroupId())) {
			return;
		}

		boolean isChatLeft = event.getGroupId() == InterfaceID.CHAT_LEFT;

		// Use invokeLater to ensure widget is fully populated before reading
		clientThread.invokeLater(() -> {
			// Filter out player dialogue (unless player voice is enabled in the future)
			if (isPlayerDialogue(isChatLeft)) {
				log.debug("Skipping player dialogue");
				// TODO: When player voice feature is added, check config here:
				// if (!config.enablePlayerVoice()) { return; }
				return;
			}

			// Validate cached NPC and refresh if null or invalid (e.g., NPC died/respawned)
			if (cachedInteractingNpc == null || cachedInteractingNpc.getId() == -1) {
				NPC interactingNpc = getCurrentInteractingNpc();
				if (interactingNpc != null) {
					cachedInteractingNpc = interactingNpc;
					log.debug("Refreshed cached NPC: {} (ID: {})", interactingNpc.getName(), interactingNpc.getId());
				}
				else {
					log.warn("Dialogue widget loaded but no valid NPC interaction found");
					return;
				}
			}

			// Read dialogue text from the appropriate widget (left or right)
			Widget dialogWidget = isChatLeft
				? client.getWidget(InterfaceID.ChatLeft.TEXT)
				: client.getWidget(InterfaceID.ChatRight.TEXT);

			if (dialogWidget == null || dialogWidget.isHidden()) {
				return;
			}

			String rawText = dialogWidget.getText();
			String cleanedText = cleanDialogueText(rawText);

			// Deduplicate: only fire event if text actually changed
			if (cleanedText.equals(lastDialogueText)) {
				return;
			}

			lastDialogueText = cleanedText;

			// Fire dialogue event
			if (dialogueCallback != null) {
				DialogueEvent dialogueEvent = createDialogueEvent(cleanedText);
				// Only fire callback if we have a valid NPC ID (not -1)
				if (dialogueEvent != null) {
					dialogueCallback.accept(dialogueEvent);
				}
			}
		});
	}

	/**
	 * Cleanup when dialogue widget closes.
	 * Resets state and fires dialogue end callback.
	 */
	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		// Detect NPC dialogue window closing (CHAT_LEFT or CHAT_RIGHT)
		if (isChatDialogueInterface(event.getGroupId())) {
			fireDialogueEndCallback("Dialogue window closed");
			resetDialogueState();
		}
	}

	/**
	 * Strips HTML tags and normalizes whitespace in dialogue text.
	 */
	private String cleanDialogueText(String rawText) {
		return rawText.replaceAll("<[^>]*>", " ")  // Remove HTML tags
			.replaceAll("\\s+", " ")                // Normalize whitespace
			.trim();
	}

	/**
	 * Creates a DialogueEvent with NPC details and animation ID.
	 * Only called when we have new dialogue to report.
	 * Returns null if the NPC ID is invalid (e.g., NPC has despawned).
	 */
	@Nullable
	private DialogueEvent createDialogueEvent(String cleanedText) {
		int npcId = cachedInteractingNpc.getId();

		// Validate NPC ID - RuneLite returns -1 for despawned/invalid NPCs
		// This is a safety net; we should have already refreshed the NPC earlier
		if (npcId == -1) {
			log.debug("Skipping dialogue event - NPC has invalid ID despite refresh attempt");
			return null;
		}

		String npcName = cachedInteractingNpc.getName();
		Integer animationId = extractAnimationId();

		// Get player name for text sanitization (replacing player name with "Adventurer")
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer != null ? localPlayer.getName() : null;

		log.debug("Dialogue: {} ({}): '{}'{}",
			npcName, npcId, cleanedText,
			animationId != null ? " [anim:" + animationId + "]" : "");

		return new DialogueEvent(
			npcId,
			npcName != null ? npcName : "Unknown",
			cleanedText,
			DialogueSource.DIALOGUE_BOX,
			animationId,
			playerName
		);
	}

	/**
	 * Extracts animation ID for emotion tracking.
	 * Tries dialogue widget HEAD animation first (both left and right), falls back to NPC animation.
	 */
	@Nullable
	private Integer extractAnimationId() {
		// Try left widget animation (more reliable for dialogue emotions)
		Integer animId = getWidgetAnimationId(InterfaceID.ChatLeft.HEAD);
		if (animId != null) {
			return animId;
		}

		// Try right widget animation
		animId = getWidgetAnimationId(InterfaceID.ChatRight.HEAD);
		if (animId != null) {
			return animId;
		}

		// Fall back to NPC's current animation
		int npcAnimId = cachedInteractingNpc.getAnimation();
		return npcAnimId != -1 ? npcAnimId : null;
	}

	/**
	 * Gets animation ID from a widget component.
	 * Returns null if widget doesn't exist or has no animation.
	 */
	@Nullable
	private Integer getWidgetAnimationId(int widgetId) {
		Widget widget = client.getWidget(widgetId);
		if (widget != null) {
			int animId = widget.getAnimationId();
			if (animId != -1) {
				return animId;
			}
		}
		return null;
	}

	/**
	 * Gets the NPC the player is currently interacting with.
	 * Returns null if not interacting or interacting with a player.
	 */
	@Nullable
	private NPC getCurrentInteractingNpc() {
		Player player = client.getLocalPlayer();
		if (player == null) {
			return null;
		}

		Actor interacting = player.getInteracting();
		return interacting instanceof NPC ? (NPC) interacting : null;
	}

	/**
	 * Checks if the given group ID is a chat dialogue interface (CHAT_LEFT or CHAT_RIGHT).
	 */
	private boolean isChatDialogueInterface(int groupId) {
		return groupId == InterfaceID.CHAT_LEFT || groupId == InterfaceID.CHAT_RIGHT;
	}

	/**
	 * Checks if the current dialogue is player dialogue (not NPC dialogue).
	 * Compares the name shown in the dialogue widget with the local player's name.
	 *
	 * @param isChatLeft true if checking CHAT_LEFT interface, false for CHAT_RIGHT
	 * @return true if this is player dialogue, false otherwise
	 */
	private boolean isPlayerDialogue(boolean isChatLeft) {
		Widget nameWidget = isChatLeft
			? client.getWidget(InterfaceID.ChatLeft.NAME)
			: client.getWidget(InterfaceID.ChatRight.NAME);

		if (nameWidget != null && !nameWidget.isHidden()) {
			String dialogueName = nameWidget.getText();
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null && dialogueName != null) {
				String playerName = localPlayer.getName();
                return dialogueName.equals(playerName);
			}
		}
		return false;
	}

	/**
	 * Fires the dialogue end callback if conditions are met.
	 * Called when dialogue ends (chat menu appears or dialogue closes).
	 */
	private void fireDialogueEndCallback(String reason) {
		if (cachedInteractingNpc != null && dialogueEndCallback != null) {
			int npcId = cachedInteractingNpc.getId();
			// Validate NPC ID before firing callback (NPC may have despawned)
			if (npcId == -1) {
				log.debug("Skipping dialogue end callback - NPC has invalid ID");
				return;
			}
			log.debug("{} - dialogue ended with NPC ID: {}", reason, npcId);
			dialogueEndCallback.accept(npcId);
		}
	}

	/**
	 * Resets dialogue tracking state when widget closes.
	 * Keeps cachedInteractingNpc - it persists until a new interaction starts.
	 */
	private void resetDialogueState() {
		lastDialogueText = "";
		log.debug("Resetting Dialogue State");
		// Don't clear cachedInteractingNpc - it persists for the entire interaction
	}
}
