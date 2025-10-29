package com.gielinorspeaks;

import com.gielinorspeaks.api.VoiceApiClient;
import com.gielinorspeaks.audio.AudioPlayer;
import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.VoiceState;
import com.gielinorspeaks.service.DialogueDetectionService;
import com.gielinorspeaks.service.OverheadTextService;
import com.gielinorspeaks.service.StatusIndicatorService;
import com.gielinorspeaks.service.VoiceOrchestrationService;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Gielinor Speaks"
)
public class GielinorSpeaksPlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private GielinorSpeaksConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DialogueDetectionService dialogueDetectionService;

	@Inject
	private OverheadTextService overheadTextService;

	// Voice services - initialized in startUp()
	private VoiceApiClient apiClient;
	private AudioPlayer audioPlayer;
	private VoiceOrchestrationService voiceService;
	private StatusIndicatorService statusIndicatorService;

	// Track the currently interacting NPC ID
	private volatile Integer currentInteractingNpcId = null;

	@Override
	protected void startUp() {
		log.info("Gielinor Speaks has started!");

		// Initialize voice services
		apiClient = new VoiceApiClient(config.apiBaseUrl());
		audioPlayer = new AudioPlayer();
		voiceService = new VoiceOrchestrationService(apiClient, audioPlayer);

		// Initialize status indicator service
		statusIndicatorService = new StatusIndicatorService(overlayManager, client, config);

		// Apply volume and mute settings from config
		audioPlayer.setVolume(config.volume());
		audioPlayer.setMuted(config.muted());

		// Set up state change callback to update status indicator
		voiceService.setStateChangeCallback((state, streamCount) -> {
			statusIndicatorService.updateState(state, streamCount);
		});

		// Check API health
		apiClient.checkHealth().thenAccept(healthy -> {
			if (healthy) {
				log.info("Voiceover-mage API is healthy at {}", config.apiBaseUrl());
			} else {
				log.warn("Voiceover-mage API is not responding at {} - voices will not play", config.apiBaseUrl());
			}
		});

		// Set up callbacks for dialogue events
		if (config.enabled()) {
			dialogueDetectionService.setDialogueCallback(this::onDialogueDetected);
			dialogueDetectionService.setDialogueEndCallback(this::onDialogueEnded);
			overheadTextService.setDialogueCallback(this::onDialogueDetected);
		}

		// Register services with event bus
		eventBus.register(dialogueDetectionService);
		eventBus.register(overheadTextService);

		// Initialize status indicator service (will only show when LOADING or PLAYING)
		// No need to show on startup - it will appear automatically when dialogue is detected
		VoiceState initialState = config.enabled() ? VoiceState.IDLE : VoiceState.DISABLED;
		statusIndicatorService.show(initialState);
	}

	@Override
	protected void shutDown() {
		log.info("Gielinor Speaks has stopped!");

		// Hide status indicator
		if (statusIndicatorService != null) {
			statusIndicatorService.hide();
		}

		// Shutdown voice services
		if (voiceService != null) {
			voiceService.shutdown();
		}

		// Unregister services from event bus
		eventBus.unregister(dialogueDetectionService);
		eventBus.unregister(overheadTextService);

		// Clear callbacks
		dialogueDetectionService.setDialogueCallback(null);
		dialogueDetectionService.setDialogueEndCallback(null);
		overheadTextService.setDialogueCallback(null);
		voiceService.setStateChangeCallback(null);
	}

	/**
	 * Handle detected dialogue events from both sources
	 */
	private void onDialogueDetected(DialogueEvent event) {
		if (!config.enabled()) {
			return;
		}

		// Check source-specific config
		if (event.getSource() == com.gielinorspeaks.model.DialogueSource.DIALOGUE_BOX && !config.enableDialogueBox()) {
			return;
		}
		if (event.getSource() == com.gielinorspeaks.model.DialogueSource.OVERHEAD_TEXT && !config.enableOverheadText()) {
			return;
		}

		// For dialogue box: this is the interacting NPC, update tracking
		if (event.getSource() == com.gielinorspeaks.model.DialogueSource.DIALOGUE_BOX) {
			currentInteractingNpcId = event.getNpcId();
		}

		// ONLY play audio for the currently interacting NPC
		// This prevents:
		// - Multiple NPCs talking over each other
		// - Background overhead text interrupting focused conversations
		// - Audio chaos in crowded areas
		//
		// Future: Add distance-based filtering for overhead text
		if (currentInteractingNpcId != null && currentInteractingNpcId.equals(event.getNpcId())) {
			voiceService.handleDialogue(event);
		} else {
			log.debug("Ignoring dialogue from NPC {} - not interacting (current: {})",
				event.getNpcId(), currentInteractingNpcId);
		}
	}

	/**
	 * Handle dialogue end events (when player options appear or dialogue closes)
	 */
	private void onDialogueEnded(int npcId) {
		if (!config.enabled()) {
			return;
		}

		// Clear interacting NPC tracking
		if (currentInteractingNpcId != null && currentInteractingNpcId == npcId) {
			currentInteractingNpcId = null;
		}

		// Delegate to voice orchestration service
		voiceService.handleDialogueEnd(npcId);
	}

	/**
	 * Handle config changes to apply settings in real-time
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("gielinorspeaks")) {
			return;
		}

		switch (event.getKey()) {
			case "volume":
				if (audioPlayer != null) {
					audioPlayer.setVolume(config.volume());
					log.debug("Volume updated to {}%", config.volume());
				}
				break;

			case "muted":
				if (audioPlayer != null) {
					audioPlayer.setMuted(config.muted());
					log.debug("Muted set to {}", config.muted());
				}
				break;

			case "enabled":
				if (statusIndicatorService != null) {
					VoiceState newState = config.enabled() ? VoiceState.IDLE : VoiceState.DISABLED;
					statusIndicatorService.updateState(newState, 0);
				}
				// Stop all audio when plugin is disabled
				if (!config.enabled() && audioPlayer != null) {
					log.info("Plugin disabled - stopping all audio");
					audioPlayer.stopAll();
				}
				break;

			case "showStatusIndicator":
				// The overlay checks this config each render, no action needed
				break;
		}
	}

	@SuppressWarnings("unused") // Used by RuneLite dependency injection
	@Provides
    GielinorSpeaksConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GielinorSpeaksConfig.class);
	}
}
