package com.gielinorspeaks.model;

import com.gielinorspeaks.api.model.SpeakRequest;
import lombok.Value;
import javax.annotation.Nullable;

/**
 * Immutable value object representing a single dialogue occurrence.
 * Contains all information needed to identify and retrieve voice audio for an NPC's dialogue.
 */
@Value
public class DialogueEvent {
	/**
	 * The NPC's game ID
	 */
	int npcId;

	/**
	 * The NPC's display name
	 */
	String npcName;

	/**
	 * The dialogue text spoken by the NPC
	 */
	String dialogueText;

	/**
	 * The source of the dialogue (dialogue box or overhead text)
	 */
	DialogueSource source;

	/**
	 * The animation ID associated with the dialogue.
	 * Only present for DIALOGUE_BOX sources.
	 * Used to capture emotion (e.g., CHATLAUGH1, CHATNEU) for voice synthesis.
	 */
	@Nullable
	Integer animationId;

	/**
	 * The player's character name.
	 * Used to replace player name with "Adventurer" in dialogue for better voice synthesis.
	 */
	@Nullable
	String playerName;

	/**
	 * Convert this dialogue event to an API speak request.
	 * Includes animation ID for emotion mapping if available.
	 * Replaces player name with "Adventurer" for better voice synthesis.
	 *
	 * @return A SpeakRequest suitable for the voiceover-mage API
	 */
	public SpeakRequest toSpeakRequest() {
		String sanitizedText = dialogueText;

		// Replace player name with "Adventurer" for better voice synthesis
		if (playerName != null && !playerName.isEmpty()) {
			sanitizedText = dialogueText.replace(playerName, "Adventurer");
		}

		return SpeakRequest.of(sanitizedText, animationId);
	}
}
