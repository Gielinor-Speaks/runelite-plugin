package com.gielinorspeaks.service;

import com.gielinorspeaks.GielinorSpeaksConfig;
import com.gielinorspeaks.model.VoiceState;
import com.gielinorspeaks.ui.VoiceStatusOverlay;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the voice status indicator overlay.
 * Handles creating, updating, and removing the status indicator based on plugin state.
 */
@Slf4j
@Singleton
public class StatusIndicatorService {
	private final OverlayManager overlayManager;
	private final Client client;
	private final GielinorSpeaksConfig config;
	private final Map<VoiceState, BufferedImage> stateIcons;

	private VoiceStatusOverlay overlay;

	@Inject
	public StatusIndicatorService(OverlayManager overlayManager, Client client, GielinorSpeaksConfig config) {
		this.overlayManager = overlayManager;
		this.client = client;
		this.config = config;
		this.stateIcons = loadStateIcons();
	}

	/**
	 * Load all state icons from resources.
	 * Tries to load from SVG/PNG resources, falls back to programmatic OSRS-style icons.
	 *
	 * @return Map of state to icon
	 */
	private Map<VoiceState, BufferedImage> loadStateIcons() {
		Map<VoiceState, BufferedImage> icons = new HashMap<>();

		// Try to load icons from resources, fall back to programmatic OSRS-style icons
		icons.put(VoiceState.DISABLED, loadIconOrCreateOSRS(VoiceState.DISABLED));
		icons.put(VoiceState.IDLE, loadIconOrCreateOSRS(VoiceState.IDLE));
		icons.put(VoiceState.LOADING, loadIconOrCreateOSRS(VoiceState.LOADING));
		icons.put(VoiceState.PLAYING, loadIconOrCreateOSRS(VoiceState.PLAYING));

		return icons;
	}

	/**
	 * Load an icon from resources, or create OSRS-style programmatic icon if not found.
	 *
	 * @param state The voice state
	 * @return The loaded or generated icon
	 */
	private BufferedImage loadIconOrCreateOSRS(VoiceState state) {
		// Map states to icon filenames in /ui/status/ directory
		String filename;
		switch (state) {
			case DISABLED:
				filename = "/ui/status/muted.png";
				break;
			case IDLE:
				filename = "/ui/status/idle.png";
				break;
			case LOADING:
				filename = "/ui/status/loading.png";
				break;
			case PLAYING:
				filename = "/ui/status/playing.png";
				break;
			default:
				filename = "/ui/status/idle.png";
		}

		try {
			BufferedImage image = ImageUtil.loadImageResource(getClass(), filename);
			if (image != null) {
				log.info("Successfully loaded icon: {}", filename);
				return image;
			}
		} catch (Exception e) {
			log.warn("Failed to load icon: {} - {}", filename, e.getMessage());
		}

		// Fallback to programmatic icon if file loading fails
		log.warn("Creating fallback programmatic icon for state: {}", state);
		return createOSRSIcon(state);
	}

	/**
	 * Create a professional OSRS-style mouth with sound waves icon.
	 * Rich shading, highlights, and three-dimensional appearance matching OSRS quality.
	 * This is a fallback when PNG resources aren't available.
	 *
	 * @param state The voice state
	 * @return Professional OSRS-style mouth icon
	 */
	private BufferedImage createOSRSIcon(VoiceState state) {
		// Create at a reasonable size for fallback
		int size = 32;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		// Transparent background
		g.setColor(new Color(0, 0, 0, 0));
		g.fillRect(0, 0, size, size);

		// Rich OSRS color palette with shading
		Color black = new Color(0, 0, 0);
		Color darkBrown = new Color(66, 44, 24);

		// Lip colors (4 shades for depth)
		Color lipDarkest = new Color(120, 50, 50);   // Shadow
		Color lipDark = new Color(160, 70, 70);      // Base dark
		Color lipMid = new Color(200, 100, 100);     // Mid-tone
		Color lipLight = new Color(230, 155, 155);   // Highlight

		// Inner mouth
		Color mouthInner = new Color(26, 13, 0);     // Very dark brown
		Color teethWhite = new Color(255, 255, 255);

		// Sound wave colors (3 shades with transparency)
		Color waveDark = new Color(91, 155, 213, 200);
		Color waveMid = new Color(135, 206, 235, 180);
		Color waveLight = new Color(212, 232, 255, 220);

		if (state == VoiceState.DISABLED) {
			// Gray disabled mouth
			lipDarkest = new Color(100, 100, 100);
			lipDark = new Color(130, 130, 130);
			lipMid = new Color(160, 160, 160);
			lipLight = new Color(190, 190, 190);
		}

		// Draw mouth with rich shading
		// Black outline (thick)
		g.setColor(black);
		g.fillRect(5, 8, 10, 5);     // Main outline
		g.fillRect(4, 9, 1, 3);      // Left edge
		g.fillRect(15, 9, 1, 3);     // Right edge
		g.fillRect(6, 7, 8, 1);      // Top edge
		g.fillRect(6, 13, 8, 1);     // Bottom edge

		// Inner mouth darkness (cavity)
		g.setColor(mouthInner);
		g.fillRect(7, 9, 6, 3);      // Inner cavity

		// Upper lip with highlight
		g.setColor(lipDark);
		g.fillRect(6, 8, 8, 1);      // Upper lip base
		g.setColor(lipLight);
		g.fillRect(7, 8, 6, 1);      // Upper lip highlight

		// Lower lip with shadow
		g.setColor(lipMid);
		g.fillRect(6, 12, 8, 1);     // Lower lip base
		g.setColor(lipDarkest);
		g.fillRect(7, 12, 6, 1);     // Lower lip shadow

		// Side shading for 3D effect
		g.setColor(lipMid);
		g.fillRect(5, 9, 1, 3);      // Left side
		g.fillRect(14, 9, 1, 3);     // Right side

		g.setColor(lipDark);
		g.fillRect(6, 9, 1, 3);      // Left inner
		g.fillRect(13, 9, 1, 3);     // Right inner

		// Small teeth for detail (optional, looks good)
		if (state != VoiceState.DISABLED) {
			g.setColor(teethWhite);
			g.fillRect(9, 9, 2, 1);  // Small teeth
		}

		// State-specific details
		switch (state) {
			case DISABLED:
				// Red X over mouth
				g.setColor(new Color(200, 50, 50));
				// Diagonal line top-left to bottom-right
				for (int i = 0; i < 10; i++) {
					g.fillRect(4 + i, 7 + i, 1, 1);
				}
				// Diagonal line top-right to bottom-left
				for (int i = 0; i < 10; i++) {
					g.fillRect(14 - i, 7 + i, 1, 1);
				}
				break;

			case LOADING:
				// Animated loading dots
				g.setColor(new Color(255, 200, 100, 220));
				g.fillRect(16, 9, 1, 1);
				g.fillRect(17, 10, 1, 1);
				g.fillRect(16, 11, 1, 1);
				break;

			case PLAYING:
				// Professional curved sound waves with anti-aliasing

				// Wave 1 (closest, brightest)
				g.setColor(waveLight);
				g.fillRect(16, 8, 1, 1);
				g.fillRect(16, 9, 1, 1);
				g.fillRect(16, 10, 1, 1);
				g.fillRect(16, 11, 1, 1);
				g.fillRect(16, 12, 1, 1);

				g.setColor(waveMid);
				g.fillRect(16, 7, 1, 1);   // Top curve
				g.fillRect(16, 13, 1, 1);  // Bottom curve

				// Wave 2 (medium distance)
				g.setColor(waveMid);
				g.fillRect(17, 6, 1, 1);
				g.fillRect(17, 7, 1, 1);
				g.fillRect(17, 8, 1, 1);
				g.fillRect(17, 12, 1, 1);
				g.fillRect(17, 13, 1, 1);
				g.fillRect(17, 14, 1, 1);

				g.setColor(waveLight);
				g.fillRect(17, 9, 1, 1);   // Highlight
				g.fillRect(17, 10, 1, 1);
				g.fillRect(17, 11, 1, 1);

				// Wave 3 (farthest, most faded)
				g.setColor(waveDark);
				g.fillRect(18, 5, 1, 1);
				g.fillRect(18, 6, 1, 1);
				g.fillRect(18, 14, 1, 1);
				g.fillRect(18, 15, 1, 1);

				g.setColor(waveMid);
				g.fillRect(18, 7, 1, 1);
				g.fillRect(18, 8, 1, 1);
				g.fillRect(18, 12, 1, 1);
				g.fillRect(18, 13, 1, 1);

				g.setColor(waveLight);
				g.fillRect(18, 9, 1, 1);   // Center highlight
				g.fillRect(18, 10, 1, 1);
				g.fillRect(18, 11, 1, 1);
				break;

			case IDLE:
				// Just the mouth, no waves
				break;
		}

		g.dispose();
		return image;
	}

	/**
	 * Get the main color for a voice state.
	 */
	private Color getStateColor(VoiceState state) {
		switch (state) {
			case DISABLED:
				return new Color(128, 128, 128); // Gray
			case IDLE:
				return Color.WHITE;
			case LOADING:
				return new Color(255, 170, 0); // Orange
			case PLAYING:
				return Color.GREEN;
			default:
				return Color.WHITE;
		}
	}

	/**
	 * Initialize and show the status indicator overlay.
	 * Should be called when the plugin starts up.
	 *
	 * @param initialState The initial state to display
	 */
	public void show(VoiceState initialState) {
		if (!config.showStatusIndicator()) {
			return;
		}

		// Remove existing overlay if present
		hide();

		// Create and add new overlay
		overlay = new VoiceStatusOverlay(client, config, stateIcons);
		overlay.updateState(initialState, 0);
		overlayManager.add(overlay);
		log.debug("Status indicator overlay added with state: {}", initialState);
	}

	/**
	 * Hide and remove the status indicator overlay.
	 * Should be called when the plugin shuts down or when disabled in config.
	 */
	public void hide() {
		if (overlay != null) {
			overlayManager.remove(overlay);
			overlay = null;
			log.debug("Status indicator overlay removed");
		}
	}

	/**
	 * Update the status indicator to show a new state.
	 *
	 * @param newState The new state
	 * @param streamCount Number of active audio streams
	 */
	public void updateState(VoiceState newState, int streamCount) {
		// If indicator is disabled in config, ensure it's hidden
		if (!config.showStatusIndicator()) {
			hide();
			return;
		}

		// If overlay doesn't exist but should, create it
		if (overlay == null) {
			show(newState);
			return;
		}

		// Update existing overlay
		overlay.updateState(newState, streamCount);
		log.debug("Status indicator updated: state={}, streams={}", newState, streamCount);
	}

	/**
	 * Get the current state shown by the indicator.
	 *
	 * @return Current state, or null if indicator is not shown
	 */
	public VoiceState getCurrentState() {
		return overlay != null ? overlay.getCurrentState() : null;
	}

	/**
	 * Get the state icons map for use by the overlay.
	 *
	 * @return Map of state to icon
	 */
	public Map<VoiceState, BufferedImage> getStateIcons() {
		return stateIcons;
	}
}
