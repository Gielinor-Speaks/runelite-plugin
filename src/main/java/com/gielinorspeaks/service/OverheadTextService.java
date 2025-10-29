package com.gielinorspeaks.service;

import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.DialogueSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Consumer;

/**
 * Service responsible for detecting overhead text from NPCs.
 * Subscribes to OverheadTextChanged events and filters for NPC actors.
 * Can handle multiple NPCs speaking simultaneously.
 */
@Slf4j
@Singleton
public class OverheadTextService {
	private final Client client;

	/**
	 * Callback to be invoked when overhead text is detected
	 */
	@Setter
	private Consumer<DialogueEvent> dialogueCallback;

	@Inject
	public OverheadTextService(Client client) {
		this.client = client;
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event) {
		Actor actor = event.getActor();

		// Filter for NPCs only (not players)
		if (!isNpcActor(actor)) {
			return;
		}

		NPC npc = (NPC) actor;
		String overheadText = event.getOverheadText();

		// Ignore empty or null text
		if (overheadText == null || overheadText.isEmpty()) {
			return;
		}

		int npcId = npc.getId();

		// Validate NPC ID - RuneLite returns -1 for despawned/invalid NPCs
		if (npcId == -1) {
			log.debug("Skipping overhead text - NPC has invalid ID");
			return;
		}

		String npcName = npc.getName();

		// Get player name for text sanitization (replacing player name with "Adventurer")
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer != null ? localPlayer.getName() : null;

		log.debug("Detected overhead text - NPC: {} (ID: {}), Text: '{}'",
			npcName, npcId, overheadText);

		DialogueEvent dialogueEvent = new DialogueEvent(
			npcId,
			npcName != null ? npcName : "Unknown",
			overheadText,
			DialogueSource.OVERHEAD_TEXT,
			null,  // No animation data for overhead text
			playerName
		);

		if (dialogueCallback != null) {
			dialogueCallback.accept(dialogueEvent);
		}
	}

	/**
	 * Check if the actor is an NPC (not a player)
	 */
	private boolean isNpcActor(Actor actor)
	{
		return actor instanceof NPC;
	}
}
