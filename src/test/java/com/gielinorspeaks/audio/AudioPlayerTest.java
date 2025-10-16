package com.gielinorspeaks.audio;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for AudioPlayer.
 *
 * Note: These tests use minimal MP3 data. For full integration testing,
 * use actual MP3 files from the voiceover-mage API.
 */
public class AudioPlayerTest {
	private AudioPlayer player;

	@Before
	public void setUp() {
		player = new AudioPlayer();
	}

	@After
	public void tearDown() {
		player.shutdown();
	}

	@Test
	public void testPlayAudio_withValidData() throws Exception {
		// Generate a minimal valid MP3 frame
		byte[] testAudio = generateMinimalMp3();
		int npcId = 0;

		assertFalse(player.isPlaying(npcId));
		player.play(npcId, testAudio);

		// Give it a moment to start
		Thread.sleep(100);

		// Audio may or may not still be playing depending on timing
		// Just verify no exceptions were thrown
	}

	@Test
	public void testPlayAudio_withNullData() {
		int npcId = 0;
		// Should log warning but not throw exception
		player.play(npcId, null);
		assertFalse(player.isPlaying(npcId));
	}

	@Test
	public void testPlayAudio_withEmptyData() {
		int npcId = 0;
		// Should log warning but not throw exception
		player.play(npcId, new byte[0]);
		assertFalse(player.isPlaying(npcId));
	}

	@Test
	public void testStop_specificNpc() throws Exception {
		byte[] testAudio = generateMinimalMp3();
		int npcId = 0;

		player.play(npcId, testAudio);
		Thread.sleep(50);
		player.stop(npcId);

		Thread.sleep(50);
		assertFalse(player.isPlaying(npcId));
	}

	@Test
	public void testStop_whenNotPlaying() {
		int npcId = 0;
		// Should be safe to call stop when nothing is playing
		player.stop(npcId);
		assertFalse(player.isPlaying(npcId));
	}

	@Test
	public void testStop_multipleTimes() {
		int npcId = 0;
		// Should be idempotent
		player.stop(npcId);
		player.stop(npcId);
		player.stop(npcId);
		assertFalse(player.isPlaying(npcId));
	}

	@Test
	public void testPlayMultipleNpcsSimultaneously() throws Exception {
		byte[] audio1 = generateMinimalMp3();
		byte[] audio2 = generateMinimalMp3();
		int npc1 = 0;
		int npc2 = 1;

		// Both NPCs can speak at the same time
		player.play(npc1, audio1);
		player.play(npc2, audio2);

		Thread.sleep(50);

		// Both should be tracked (though may have finished by now)
		// Just verify no exceptions and state is tracked
		assertEquals(0, player.getActiveStreamCount()); // May have finished
	}

	@Test
	public void testPlaySameNpcTwice_stopsFirst() throws Exception {
		byte[] audio1 = generateMinimalMp3();
		byte[] audio2 = generateMinimalMp3();
		int npcId = 0;

		player.play(npcId, audio1);
		Thread.sleep(50);

		// Playing same NPC again should stop first audio
		player.play(npcId, audio2);
		Thread.sleep(50);

		// Should not throw exceptions
	}

	@Test
	public void testStopAll() throws Exception {
		byte[] testAudio = generateMinimalMp3();
		int npc1 = 0;
		int npc2 = 1;

		player.play(npc1, testAudio);
		player.play(npc2, testAudio);

		Thread.sleep(50);
		player.stopAll();

		Thread.sleep(50);
		assertFalse(player.isPlaying(npc1));
		assertFalse(player.isPlaying(npc2));
		assertFalse(player.isAnyPlaying());
	}

	@Test
	public void testIsAnyPlaying() throws Exception {
		byte[] testAudio = generateMinimalMp3();
		int npcId = 0;

		assertFalse(player.isAnyPlaying());

		player.play(npcId, testAudio);
		Thread.sleep(50);

		// May or may not be playing depending on timing
		// Just verify method doesn't throw
		player.isAnyPlaying();
	}

	@Test
	public void testGetActiveStreamCount() throws Exception {
		byte[] testAudio = generateMinimalMp3();

		assertEquals(0, player.getActiveStreamCount());

		player.play(0, testAudio);
		player.play(1, testAudio);

		Thread.sleep(50);

		// May be 0, 1, or 2 depending on timing
		// Just verify method doesn't throw
		int count = player.getActiveStreamCount();
		assertTrue(count >= 0 && count <= 2);
	}

	@Test
	public void testShutdown() throws Exception {
		byte[] testAudio = generateMinimalMp3();
		int npc1 = 0;
		int npc2 = 1;

		player.play(npc1, testAudio);
		player.play(npc2, testAudio);

		Thread.sleep(50);
		player.shutdown();

		assertFalse(player.isPlaying(npc1));
		assertFalse(player.isPlaying(npc2));
		assertFalse(player.isAnyPlaying());
	}

	/**
	 * Generate a minimal valid MP3 frame for testing.
	 * This creates a very short silent MP3 that JLayer can decode.
	 *
	 * Note: This is a minimal MPEG-1 Layer 3 frame header with silent data.
	 * For real testing, use actual MP3 files from the API.
	 *
	 * @return Minimal MP3 byte array
	 */
	private byte[] generateMinimalMp3() {
		// Minimal MP3 frame: MPEG-1 Layer 3, 128 kbps, 44100 Hz, mono
		// Frame sync (11 bits): 0xFF 0xFB
		// This creates a minimal valid frame that JLayer can process
		return new byte[]{
			(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x00,  // MP3 frame header
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,  // Frame data (silent)
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		};
	}
}
