package com.gielinorspeaks.service;

import com.gielinorspeaks.api.VoiceApiClient;
import com.gielinorspeaks.api.model.ApiException;
import com.gielinorspeaks.api.model.SpeakResponse;
import com.gielinorspeaks.audio.AudioPlayer;
import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.VoiceState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Orchestrates voice generation and playback for NPC dialogue.
 *
 * This service coordinates:
 * - Receiving dialogue events from detection services
 * - Queuing dialogue for sequential playback (no overlapping audio)
 * - Requesting speech from the voiceover-mage API
 * - Playing audio through AudioPlayer
 * - Error handling and logging
 * - Audio interruption: When an NPC speaks again, their current audio is stopped immediately
 *   and their queued dialogue is replaced (clicking "continue" stops the previous audio)
 *
 * Queue behavior: Dialogue plays one at a time in the order received. When clicking "continue",
 * the current NPC's playing audio is stopped and any queued dialogue from that NPC is removed.
 *
 * Thread Safety: All callbacks execute on the client thread (RuneLite's event bus pattern).
 * The service maintains minimal state and delegates playback to AudioPlayer which is thread-safe.
 */
@Slf4j
@Singleton
public class VoiceOrchestrationService {
	private final VoiceApiClient apiClient;
	private final AudioPlayer audioPlayer;

	// State tracking
	private volatile VoiceState currentState = VoiceState.IDLE;
	private volatile BiConsumer<VoiceState, Integer> stateChangeCallback;

	// Audio queue - ensures dialogue plays sequentially, not simultaneously
	private final ConcurrentLinkedQueue<DialogueEvent> audioQueue = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);

	@Inject
	public VoiceOrchestrationService(VoiceApiClient apiClient, AudioPlayer audioPlayer) {
		this.apiClient = apiClient;
		this.audioPlayer = audioPlayer;

		// Set callback to process next queued item when playback completes
		this.audioPlayer.setPlaybackCompleteCallback(this::onPlaybackComplete);
	}

	/**
	 * Set a callback to be notified when the voice state changes.
	 *
	 * @param callback Callback that receives (state, activeStreamCount)
	 */
	public void setStateChangeCallback(BiConsumer<VoiceState, Integer> callback) {
		this.stateChangeCallback = callback;
	}

	/**
	 * Handle a detected dialogue event.
	 * Adds the event to the queue for sequential playback.
	 *
	 * If the same NPC has dialogue already queued or playing, it is removed/stopped
	 * before the new dialogue is queued. This ensures clicking "continue"
	 * immediately stops the previous line.
	 *
	 * @param event The dialogue event containing NPC info and text
	 */
	public void handleDialogue(DialogueEvent event) {
		int npcId = event.getNpcId();

		// Stop any currently playing audio for this NPC immediately
		audioPlayer.stop(npcId);

		// Remove any queued events for this NPC (user clicked continue)
		audioQueue.removeIf(queuedEvent -> queuedEvent.getNpcId() == npcId);

		// Add new event to queue
		audioQueue.offer(event);

		// Start processing queue if not already processing
		processNextInQueue();
	}

	/**
	 * Process the next dialogue event in the queue.
	 * Only processes if not currently processing and queue is not empty.
	 */
	private void processNextInQueue() {
		// Try to acquire the processing lock
		if (!isProcessingQueue.compareAndSet(false, true)) {
			// Already processing
			return;
		}

		// Get next event from queue
		DialogueEvent event = audioQueue.poll();
		if (event == null) {
			// Queue is empty, release lock
			isProcessingQueue.set(false);
			updateStateBasedOnPlayback();
			return;
		}

		// Update state to LOADING
		updateState(VoiceState.LOADING);

		// Request speech from API
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
		// Stop this NPC's audio
		audioPlayer.stop(npcId);

		// Clear queue and update state
		audioQueue.clear();
		isProcessingQueue.set(false);
		updateStateBasedOnPlayback();
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
			// No audio data, move to next in queue
			isProcessingQueue.set(false);
			processNextInQueue();
			return;
		}

		// Play audio for this specific NPC
		// When playback completes, onPlaybackComplete() will be called
		audioPlayer.play(event.getNpcId(), audioData);

		// Update state to PLAYING
		updateState(VoiceState.PLAYING);
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

		// Only log non-retryable errors
		if (cause instanceof ApiException) {
			ApiException apiEx = (ApiException) cause;
			if (!apiEx.isRetryable()) {
				log.error("API error: {} - {}", apiEx.getErrorType(), apiEx.getMessage());
			}
		} else {
			log.error("Unexpected error fetching audio", cause);
		}

		// Move to next in queue
		isProcessingQueue.set(false);
		processNextInQueue();

		return null;
	}

	/**
	 * Called when audio playback completes.
	 * Processes the next item in the queue.
	 */
	private void onPlaybackComplete() {
		// Release processing lock and process next item
		isProcessingQueue.set(false);
		processNextInQueue();
	}

	/**
	 * Update the current state and notify listeners.
	 *
	 * @param newState The new state
	 */
	private void updateState(VoiceState newState) {
		if (currentState != newState) {
			currentState = newState;
			notifyStateChange();
		}
	}

	/**
	 * Update state based on current playback status.
	 * Sets state to PLAYING if audio is playing, otherwise IDLE.
	 */
	private void updateStateBasedOnPlayback() {
		if (audioPlayer.isAnyPlaying()) {
			updateState(VoiceState.PLAYING);
		} else {
			updateState(VoiceState.IDLE);
		}
	}

	/**
	 * Notify the state change callback with current state and stream count.
	 */
	private void notifyStateChange() {
		if (stateChangeCallback != null) {
			int streamCount = audioPlayer.getActiveStreamCount();
			stateChangeCallback.accept(currentState, streamCount);
		}
	}

	/**
	 * Get the current voice state.
	 *
	 * @return Current state
	 */
	public VoiceState getCurrentState() {
		return currentState;
	}

	/**
	 * Shutdown the service and cleanup resources.
	 * Should be called when the plugin shuts down.
	 */
	public void shutdown() {
		audioQueue.clear();
		isProcessingQueue.set(false);
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
