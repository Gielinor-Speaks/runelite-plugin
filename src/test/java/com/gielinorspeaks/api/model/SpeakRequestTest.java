package com.gielinorspeaks.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for SpeakRequest model class.
 */
public class SpeakRequestTest {

    @Test
    public void testOf_textOnly() {
        SpeakRequest request = SpeakRequest.of("Hello, adventurer!");

        assertEquals("Hello, adventurer!", request.getText());
        assertNull(request.getAnimationId());
    }

    @Test
    public void testOf_withAnimationId() {
        SpeakRequest request = SpeakRequest.of("Hello, adventurer!", 588);

        assertEquals("Hello, adventurer!", request.getText());
        assertEquals(Integer.valueOf(588), request.getAnimationId());
    }

    @Test
    public void testOf_withNullAnimationId() {
        SpeakRequest request = SpeakRequest.of("Hello, adventurer!", null);

        assertEquals("Hello, adventurer!", request.getText());
        assertNull(request.getAnimationId());
    }

    @Test
    public void testConstructor() {
        SpeakRequest request = new SpeakRequest("Test text", 123);

        assertEquals("Test text", request.getText());
        assertEquals(Integer.valueOf(123), request.getAnimationId());
    }
}
