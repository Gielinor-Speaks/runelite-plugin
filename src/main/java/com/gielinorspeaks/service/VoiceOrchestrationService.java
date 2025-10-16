package com.gielinorspeaks.service;

import com.gielinorspeaks.api.VoiceApiClient;
import com.gielinorspeaks.api.model.ApiException;
import com.gielinorspeaks.api.model.SpeakResponse;
import com.gielinorspeaks.audio.AudioPlayer;
import com.gielinorspeaks.model.DialogueEvent;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates voice generation and playback for NPC dialogue.
 *
 * This service coordinates:
 * - Receiving dialogue events from detection services
 * - Requesting speech from the voiceover-mage API
 * - Playing audio through AudioPlayer
 * - Error handling and logging
 *
 * Thread Safety: All callbacks execute on the client thread (RuneLite's event bus pattern).
 * The service maintains minimal state and delegates playback to AudioPlayer which is thread-safe.
 */
@Slf4j
@Singleton
public class VoiceOrchestrationService {
	private final VoiceApiClient apiClient;
	private final AudioPlayer audioPlayer;

	@Inject
	public VoiceOrchestrationService(VoiceApiClient apiClient, AudioPlayer audioPlayer) {
		this.apiClient = apiClient;
		this.audioPlayer = audioPlayer;
	}

	/**
	 * Handle a detected dialogue event.
	 * Requests speech from API and plays audio when ready.
	 *
	 * Each NPC can speak independently. If the same NPC speaks again,
	 * their previous audio is stopped.
	 *
	 * @param event The dialogue event containing NPC info and text
	 */
	public void handleDialogue(DialogueEvent event) {
		log.info("=== VOICE ORCHESTRATION ===");
		log.info("NPC: {} (ID: {})", event.getNpcName(), event.getNpcId());
		log.info("Text: {}", event.getDialogueText());
		log.info("Source: {}", event.getSource());
		if (event.getAnimationId() != null) {
			log.info("Animation ID: {}", event.getAnimationId());
		}
		log.info("===========================");

		// Request speech from API
		// Note: If this NPC is already speaking, their audio will be stopped
		// when the new audio arrives and starts playing
		apiClient.requestSpeech(event.getNpcId(), event.toSpeakRequest())
			.thenAccept(response -> handleApiResponse(event, response))
			.exceptionally(throwable -> handleApiError(event, throwable));
	}

	/**
	 * Handle dialogue end event.
	 * Stops audio playback for the specified NPC.
	 *
	 * @param npcId The NPC whose dialogue ended
	 */
	public void handleDialogueEnd(int npcId) {
		log.info("=== DIALOGUE ENDED ===");
		log.info("NPC ID: {}", npcId);
		log.info("======================");

		// Stop this NPC's audio
		audioPlayer.stop(npcId);
	}

	/**
	 * Handle successful API response.
	 * Extracts audio data and plays it for the specific NPC.
	 *
	 * @param event The original dialogue event
	 * @param response The API response containing audio data
	 */
	private void handleApiResponse(DialogueEvent event, SpeakResponse response) {
		byte[] audioData = response.decodeAudio();
		if (audioData == null) {
			log.warn("No audio data in response for NPC {} - {}", event.getNpcId(), event.getNpcName());
			return;
		}

		log.info("Received audio for NPC {} - {} ({} bytes, cached: {}, provider: {})",
			event.getNpcId(), event.getNpcName(), audioData.length,
			response.isCached(), response.getProvider());

		// Play audio for this specific NPC
		// If this NPC was already speaking, their old audio will be stopped first
		audioPlayer.play(event.getNpcId(), audioData);
	}

	/**
	 * Handle API error.
	 * Logs error details and determines if retry should be attempted.
	 *
	 * @param event The original dialogue event
	 * @param throwable The error that occurred
	 * @return null (required for CompletableFuture.exceptionally)
	 */
	private Void handleApiError(DialogueEvent event, Throwable throwable) {
		Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

		if (cause instanceof ApiException) {
			ApiException apiEx = (ApiException) cause;

			if (apiEx.isRetryable()) {
				log.warn("API error for NPC {} - {} (retryable): {} - {}",
					event.getNpcId(), event.getNpcName(),
					apiEx.getErrorType(), apiEx.getMessage());
				// TODO: Implement retry logic in future phase
			} else {
				log.error("API error for NPC {} - {} (not retryable): {} - {}",
					event.getNpcId(), event.getNpcName(),
					apiEx.getErrorType(), apiEx.getMessage());
			}
		} else {
			log.error("Unexpected error fetching audio for NPC {} - {}",
				event.getNpcId(), event.getNpcName(), cause);
		}

		return null;
	}

	/**
	 * Shutdown the service and cleanup resources.
	 * Should be called when the plugin shuts down.
	 */
	public void shutdown() {
		log.info("Shutting down voice orchestration service");
		audioPlayer.shutdown();
		apiClient.shutdown();
	}

	/**
	 * Check if a specific NPC is currently playing audio.
	 *
	 * @param npcId The NPC ID
	 * @return true if this NPC's audio is playing
	 */
	public boolean isPlaying(int npcId) {
		return audioPlayer.isPlaying(npcId);
	}

	/**
	 * Check if any audio is currently playing.
	 *
	 * @return true if any audio playback is in progress
	 */
	public boolean isAnyPlaying() {
		return audioPlayer.isAnyPlaying();
	}

	/**
	 * Get the number of NPCs currently playing audio.
	 *
	 * @return Count of active audio streams
	 */
	public int getActiveStreamCount() {
		return audioPlayer.getActiveStreamCount();
	}
}
