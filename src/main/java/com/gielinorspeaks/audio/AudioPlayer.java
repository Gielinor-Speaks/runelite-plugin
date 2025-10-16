package com.gielinorspeaks.audio;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for playing MP3 audio from byte arrays.
 * Uses JLayer library for MP3 decoding and playback.
 *
 * Supports multiple NPCs speaking simultaneously - each NPC has its own audio stream.
 * If the same NPC speaks again, their previous audio is stopped.
 *
 * Thread Safety: All methods are thread-safe. Each NPC's audio plays on a shared
 * thread pool, allowing concurrent playback for different NPCs.
 */
@Slf4j
@Singleton
public class AudioPlayer {
	private final ExecutorService executorService;

	// Per-NPC playback tracking
	private final Map<Integer, NpcAudioState> npcAudioStates = new ConcurrentHashMap<>();

	public AudioPlayer() {
		// Cached thread pool allows multiple NPCs to speak simultaneously
		// Threads are created as needed and reused
		this.executorService = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "GielinorSpeaks-AudioPlayer");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Play MP3 audio for a specific NPC.
	 * If this NPC is already speaking, stops their previous audio first.
	 * Other NPCs continue playing uninterrupted.
	 *
	 * @param npcId The NPC ID
	 * @param audioData MP3 audio bytes
	 */
	public void play(int npcId, byte[] audioData) {
		if (audioData == null || audioData.length == 0) {
			log.warn("Attempted to play null or empty audio data for NPC {}", npcId);
			return;
		}

		// Stop this NPC's current audio if playing
		stop(npcId);

		// Start new audio for this NPC
		NpcAudioState state = new NpcAudioState();
		npcAudioStates.put(npcId, state);

		state.playbackFuture = executorService.submit(() -> {
			try (ByteArrayInputStream bis = new ByteArrayInputStream(audioData)) {
				state.player = new AdvancedPlayer(bis);
				log.debug("Playing audio for NPC {} ({} bytes)", npcId, audioData.length);
				state.player.play();
				log.debug("Audio playback finished for NPC {}", npcId);
			} catch (JavaLayerException e) {
				log.error("Failed to play audio for NPC {} (JLayer error)", npcId, e);
			} catch (IOException e) {
				log.error("Failed to play audio for NPC {} (IO error)", npcId, e);
			} finally {
				state.player = null;
				npcAudioStates.remove(npcId);
			}
		});
	}

	/**
	 * Stop audio for a specific NPC.
	 * Other NPCs continue playing uninterrupted.
	 * This method is idempotent and safe to call even if the NPC has no audio playing.
	 *
	 * @param npcId The NPC ID
	 */
	public void stop(int npcId) {
		NpcAudioState state = npcAudioStates.remove(npcId);
		if (state != null) {
			if (state.player != null) {
				try {
					state.player.close();
				} catch (Exception e) {
					log.debug("Error closing audio player for NPC {} (may be already closed)", npcId, e);
				}
			}
			if (state.playbackFuture != null && !state.playbackFuture.isDone()) {
				state.playbackFuture.cancel(true);
			}
		}
	}

	/**
	 * Stop all currently playing audio for all NPCs.
	 */
	public void stopAll() {
		for (Integer npcId : npcAudioStates.keySet()) {
			stop(npcId);
		}
	}

	/**
	 * Check if a specific NPC is currently playing audio.
	 *
	 * @param npcId The NPC ID
	 * @return true if this NPC's audio is playing
	 */
	public boolean isPlaying(int npcId) {
		NpcAudioState state = npcAudioStates.get(npcId);
		return state != null && state.playbackFuture != null && !state.playbackFuture.isDone();
	}

	/**
	 * Check if any NPC is currently playing audio.
	 *
	 * @return true if any audio playback is in progress
	 */
	public boolean isAnyPlaying() {
		return npcAudioStates.values().stream()
			.anyMatch(state -> state.playbackFuture != null && !state.playbackFuture.isDone());
	}

	/**
	 * Get the number of NPCs currently playing audio.
	 *
	 * @return Count of active audio streams
	 */
	public int getActiveStreamCount() {
		return (int) npcAudioStates.values().stream()
			.filter(state -> state.playbackFuture != null && !state.playbackFuture.isDone())
			.count();
	}

	/**
	 * Shutdown the audio player and cleanup resources.
	 * Should be called when the plugin shuts down.
	 */
	public void shutdown() {
		log.debug("Shutting down audio player");
		stopAll();
		executorService.shutdown();
	}

	/**
	 * Holds the playback state for a single NPC.
	 */
	private static class NpcAudioState {
		volatile AdvancedPlayer player;
		volatile Future<?> playbackFuture;
	}
}
