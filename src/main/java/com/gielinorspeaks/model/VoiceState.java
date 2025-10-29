package com.gielinorspeaks.model;

/**
 * Represents the current state of the voice playback system.
 * Used by the status indicator to display the appropriate icon and text.
 */
public enum VoiceState {
	/**
	 * Plugin is disabled in config.
	 * No voice playback will occur.
	 */
	DISABLED,

	/**
	 * Plugin is enabled but not actively processing dialogue.
	 * Ready to handle dialogue events.
	 */
	IDLE,

	/**
	 * Dialogue detected, API request in progress.
	 * Waiting for audio data from voiceover-mage service.
	 */
	LOADING,

	/**
	 * Audio is currently playing.
	 * One or more NPCs are speaking.
	 */
	PLAYING
}
