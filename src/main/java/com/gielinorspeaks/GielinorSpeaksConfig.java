package com.gielinorspeaks;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gielinorspeaks")
public interface GielinorSpeaksConfig extends Config {
	@SuppressWarnings("unused") // Used by RuneLite config system
	@ConfigItem(
		keyName = "enabled",
		name = "Enable Plugin",
		description = "Enable or disable NPC voice playback"
	)
	default boolean enabled()
	{
		return true;
	}

	@SuppressWarnings("unused") // Used by RuneLite config system
	@ConfigItem(
		keyName = "enableDialogueBox",
		name = "Dialogue Box Detection",
		description = "Detect and play voices for dialogue box conversations"
	)
	default boolean enableDialogueBox()
	{
		return true;
	}

	@SuppressWarnings("unused") // Used by RuneLite config system
	@ConfigItem(
		keyName = "enableOverheadText",
		name = "Overhead Text Detection",
		description = "Detect and play voices for overhead text"
	)
	default boolean enableOverheadText()
	{
		return true;
	}

	@SuppressWarnings("unused") // Used by RuneLite config system
	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API Base URL",
		description = "Voiceover-mage API base URL (requires plugin restart)"
	)
	default String apiBaseUrl()
	{
		return "http://localhost:8000/api/v1";
	}
}
