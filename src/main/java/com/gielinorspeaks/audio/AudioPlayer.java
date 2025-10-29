package com.gielinorspeaks.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for playing MP3 audio from byte arrays with volume and mute control.
 * Uses JLayer library for MP3 decoding and javax.sound.sampled for playback.
 *
 * Supports multiple NPCs speaking simultaneously - each NPC has its own audio stream.
 * If the same NPC speaks again, their previous audio is stopped.
 *
 * Volume control: Applies gain control via FloatControl.Type.MASTER_GAIN (0-100%)
 * Mute control: Completely silences audio when enabled, regardless of volume setting
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

	// Volume control (0-100)
	private volatile int volume = 100;

	// Mute control
	private volatile boolean muted = false;

	// Callback for when playback completes
	private volatile Runnable playbackCompleteCallback;

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
	 * Set a callback to be notified when any audio playback completes.
	 *
	 * @param callback Callback to invoke when playback finishes
	 */
	public void setPlaybackCompleteCallback(Runnable callback) {
		this.playbackCompleteCallback = callback;
	}

	/**
	 * Set the playback volume for all audio.
	 * Applies to new audio playback only (does not affect currently playing audio).
	 *
	 * @param volume Volume level (0-100)
	 */
	public void setVolume(int volume) {
		this.volume = Math.max(0, Math.min(100, volume));
	}

	/**
	 * Get the current playback volume.
	 *
	 * @return Volume level (0-100)
	 */
	public int getVolume() {
		return volume;
	}

	/**
	 * Set the mute state for all audio.
	 * When muted, no audio will play regardless of volume setting.
	 * If muting, stops all currently playing audio immediately.
	 *
	 * @param muted true to mute, false to unmute
	 */
	public void setMuted(boolean muted) {
		this.muted = muted;

		// Stop all currently playing audio when muting
		if (muted) {
			stopAll();
		}
	}

	/**
	 * Get the current mute state.
	 *
	 * @return true if muted, false otherwise
	 */
	public boolean isMuted() {
		return muted;
	}

	/**
	 * Play MP3 audio for a specific NPC.
	 * If this NPC is already speaking, stops their previous audio first.
	 * Other NPCs continue playing uninterrupted.
	 *
	 * Implementation: Uses JLayer's Decoder to decode MP3 to PCM samples,
	 * then javax.sound.sampled.SourceDataLine for playback with volume control.
	 *
	 * @param npcId The NPC ID
	 * @param audioData MP3 audio bytes
	 */
	public void play(int npcId, byte[] audioData) {
		if (audioData == null || audioData.length == 0) {
			return;
		}

		// Skip playback if muted
		if (muted) {
			return;
		}

		// Skip playback if volume is 0
		if (volume == 0) {
			return;
		}

		// Stop this NPC's current audio if playing
		stop(npcId);

		// Start new audio for this NPC
		NpcAudioState state = new NpcAudioState();
		npcAudioStates.put(npcId, state);

		state.playbackFuture = executorService.submit(() -> {
			SourceDataLine line = null;
			try (ByteArrayInputStream bis = new ByteArrayInputStream(audioData)) {
				Bitstream bitstream = new Bitstream(bis);
				Decoder decoder = new Decoder();

				// Read first frame to get audio format info
				Header header = bitstream.readFrame();
				if (header == null) {
					return;
				}

				// Create audio format from MP3 header
				int sampleRate = header.frequency();
				int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
				AudioFormat format = new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED,
					sampleRate,
					16, // 16-bit samples
					channels,
					channels * 2, // frame size (2 bytes per channel)
					sampleRate,
					false // little-endian
				);

				// Open and start the audio line
				line = AudioSystem.getSourceDataLine(format);
				line.open(format);
				line.start();

				// Apply volume control
				if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
					FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
					// Convert volume 0-100 to gain in decibels
					// Volume 100 = 0dB, Volume 50 = ~-10dB, Volume 0 = minimum
					float gain;
					if (volume >= 100) {
						gain = 0.0f; // Max volume
					} else if (volume <= 0) {
						gain = volumeControl.getMinimum(); // Min volume
					} else {
						// Logarithmic scale: 0-100 maps to min-0 dB
						float range = volumeControl.getMaximum() - volumeControl.getMinimum();
						gain = volumeControl.getMinimum() + (range * volume / 100.0f);
					}
					volumeControl.setValue(Math.max(volumeControl.getMinimum(),
						Math.min(gain, volumeControl.getMaximum())));
				}

				// Store reference to line in state for external stop() calls
				state.line = line;

				// Decode and play frames
				boolean interrupted = false;
				do {
					// Check if we should stop (state removed = stop requested)
					if (!npcAudioStates.containsKey(npcId) || Thread.currentThread().isInterrupted()) {
						interrupted = true;
						break;
					}

					SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
					if (output != null) {
						// Convert short[] samples to byte[] for line
						short[] samples = output.getBuffer();
						int sampleCount = output.getBufferLength();
						byte[] bytes = new byte[sampleCount * 2];

						for (int i = 0; i < sampleCount; i++) {
							// Little-endian 16-bit PCM
							bytes[i * 2] = (byte) (samples[i] & 0xFF);
							bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
						}

						line.write(bytes, 0, bytes.length);
					}
					bitstream.closeFrame();
				} while ((header = bitstream.readFrame()) != null);

				// If interrupted, flush the line immediately; otherwise drain normally
				if (interrupted) {
					line.flush();
				} else {
					// Wait for line to finish playing buffered audio
					line.drain();
				}
			} catch (JavaLayerException e) {
				log.error("Failed to play audio for NPC {} (JLayer error)", npcId, e);
			} catch (LineUnavailableException e) {
				log.error("Failed to play audio for NPC {} (audio line unavailable)", npcId, e);
			} catch (Exception e) {
				log.error("Failed to play audio for NPC {} (unexpected error)", npcId, e);
			} finally {
				// Clean up audio line
				if (line != null) {
					line.stop();
					line.close();
				}

				state.line = null;
				npcAudioStates.remove(npcId);

				// Notify completion callback
				if (playbackCompleteCallback != null) {
					playbackCompleteCallback.run();
				}
			}
		});
	}

	/**
	 * Stop audio for a specific NPC immediately.
	 * Other NPCs continue playing uninterrupted.
	 * This method is idempotent and safe to call even if the NPC has no audio playing.
	 *
	 * @param npcId The NPC ID
	 */
	public void stop(int npcId) {
		NpcAudioState state = npcAudioStates.remove(npcId);
		if (state != null) {
			// Cancel the playback thread first
			if (state.playbackFuture != null && !state.playbackFuture.isDone()) {
				state.playbackFuture.cancel(true);
			}

			// Stop the audio line immediately
			if (state.line != null) {
				try {
					// Flush discards any queued data for immediate stop
					state.line.flush();
					state.line.stop();
					state.line.close();
				} catch (Exception e) {
					// Ignore - line may already be closed
				}
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
		stopAll();
		executorService.shutdown();
	}

	/**
	 * Holds the playback state for a single NPC.
	 */
	private static class NpcAudioState {
		volatile SourceDataLine line;
		volatile Future<?> playbackFuture;
	}
}
