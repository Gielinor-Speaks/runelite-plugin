package com.gielinorspeaks.ui;

import com.gielinorspeaks.GielinorSpeaksConfig;
import com.gielinorspeaks.model.VoiceState;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Overlay that displays the voice status indicator in the top-right corner of the chatbox.
 * Integrates naturally with the OSRS UI by positioning relative to the chatbox widget.
 */
@Slf4j
public class VoiceStatusOverlay extends Overlay {
	private final Client client;
	private final GielinorSpeaksConfig config;
	private final Map<VoiceState, BufferedImage> stateIcons;

	@Setter
	private VoiceState currentState = VoiceState.IDLE;

	@Setter
	private int activeStreamCount = 0;

	@Inject
	public VoiceStatusOverlay(Client client, GielinorSpeaksConfig config, Map<VoiceState, BufferedImage> stateIcons) {
		this.client = client;
		this.config = config;
		this.stateIcons = stateIcons;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		// Don't render if disabled in config
		if (!config.showStatusIndicator()) {
			log.debug("Status indicator disabled in config");
			return null;
		}

		// Only show when dialogue is active (hide when disabled)
		if (currentState == VoiceState.DISABLED) {
			log.debug("Not rendering - plugin is disabled");
			return null;
		}

		// Check if dialogue box is open (either left or right dialogue interface)
		// Using the text widgets as DialogueDetectionService does for detection
		Widget dialogueLeftText = client.getWidget(InterfaceID.ChatLeft.TEXT);
		Widget dialogueRightText = client.getWidget(InterfaceID.ChatRight.TEXT);

		boolean dialogueBoxOpen = (dialogueLeftText != null && !dialogueLeftText.isHidden())
								|| (dialogueRightText != null && !dialogueRightText.isHidden());

		if (!dialogueBoxOpen) {
			return null;
		}

		// Get the chatbox widget to position in its top-right corner
		Widget chatboxWidget = client.getWidget(InterfaceID.CHATBOX, 0);
		if (chatboxWidget == null || chatboxWidget.getCanvasLocation() == null) {
			log.warn("Chatbox widget or its canvas location is null!");
			return null;
		}

		// Get the icon - use muted icon if muted, otherwise use current state icon
		VoiceState iconState = config.muted() ? VoiceState.DISABLED : currentState;
		BufferedImage icon = stateIcons.get(iconState);
		if (icon == null) {
			log.warn("Icon is null for state: {}", iconState);
			return null;
		}

		// Get native icon size
		int nativeWidth = icon.getWidth();
		int nativeHeight = icon.getHeight();

		// Scale down the icon to 1/32 size for better display
		int iconWidth = nativeWidth / 32;
		int iconHeight = nativeHeight / 32;

		// Position in top-right corner of the chatbox
		int chatboxX = chatboxWidget.getCanvasLocation().getX();
		int chatboxY = chatboxWidget.getCanvasLocation().getY();
		int chatboxWidth = chatboxWidget.getWidth();
		int chatboxHeight = chatboxWidget.getHeight();

		// Position in top-right corner with minimal padding
		int padding = 2;
		int x = chatboxX + chatboxWidth - iconWidth - padding;
		int y = chatboxY + padding;

		// Use high-quality rendering hints for better scaling
		Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		Object oldRendering = graphics.getRenderingHint(RenderingHints.KEY_RENDERING);
		Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Draw the icon with scaling
		graphics.drawImage(icon, x, y, iconWidth, iconHeight, null);

		// Restore original rendering hints
		if (oldInterpolation != null) graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
		if (oldRendering != null) graphics.setRenderingHint(RenderingHints.KEY_RENDERING, oldRendering);
		if (oldAntialiasing != null) graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);

		return new Dimension(iconWidth, iconHeight);
	}

	/**
	 * Get text color based on current state.
	 */
	private Color getTextColor() {
		switch (currentState) {
			case DISABLED:
				return Color.GRAY;
			case IDLE:
				return Color.WHITE;
			case LOADING:
				return Color.YELLOW;
			case PLAYING:
				return Color.GREEN;
			default:
				return Color.WHITE;
		}
	}

	/**
	 * Update the overlay state.
	 */
	public void updateState(VoiceState newState, int streamCount) {
		this.currentState = newState;
		this.activeStreamCount = streamCount;
	}

	/**
	 * Get the current state.
	 */
	public VoiceState getCurrentState() {
		return currentState;
	}
}
