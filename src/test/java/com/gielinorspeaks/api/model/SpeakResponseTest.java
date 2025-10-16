package com.gielinorspeaks.api.model;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Tests for SpeakResponse model class.
 */
public class SpeakResponseTest {

    @Test
    public void testDecodeAudio_validBase64() {
        String base64Audio = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        SpeakResponse response = new SpeakResponse(
            0, "Hans", "test", null, base64Audio,
            false, "test", new HashMap<>()
        );

        byte[] decoded = response.decodeAudio();
        assertNotNull(decoded);
        assertEquals("Hello World", new String(decoded));
    }

    @Test
    public void testDecodeAudio_nullBase64() {
        SpeakResponse response = new SpeakResponse(
            0, "Hans", "test", null, null,
            false, "test", new HashMap<>()
        );

        assertNull(response.decodeAudio());
    }

    @Test
    public void testDecodeAudio_emptyBase64() {
        SpeakResponse response = new SpeakResponse(
            0, "Hans", "test", null, "",
            false, "test", new HashMap<>()
        );

        assertNull(response.decodeAudio());
    }

    @Test
    public void testHasAudioData_withAudio() {
        SpeakResponse response = new SpeakResponse(
            0, "Hans", "test", null, "SGVsbG8=",
            false, "test", new HashMap<>()
        );

        assertTrue(response.hasAudioData());
    }

    @Test
    public void testHasAudioData_nullAudio() {
        SpeakResponse response = new SpeakResponse(
            0, "Hans", "test", null, null,
            false, "test", new HashMap<>()
        );

        assertFalse(response.hasAudioData());
    }

    @Test
    public void testHasAudioData_emptyAudio() {
        SpeakResponse response = new SpeakResponse(
            0, "Hans", "test", null, "",
            false, "test", new HashMap<>()
        );

        assertFalse(response.hasAudioData());
    }

    @Test
    public void testGetters() {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("preview_id", 123);

        SpeakResponse response = new SpeakResponse(
            741, "Duke Horacio", "Welcome to Lumbridge!",
            null, "YXVkaW9kYXRh",
            true, "elevenlabs", metadata
        );

        assertEquals(741, response.getNpcId());
        assertEquals("Duke Horacio", response.getNpcName());
        assertEquals("Welcome to Lumbridge!", response.getText());
        assertNull(response.getAudioUrl());
        assertEquals("YXVkaW9kYXRh", response.getAudioBase64());
        assertTrue(response.isCached());
        assertEquals("elevenlabs", response.getProvider());
        assertEquals(metadata, response.getGenerationMetadata());
    }
}
