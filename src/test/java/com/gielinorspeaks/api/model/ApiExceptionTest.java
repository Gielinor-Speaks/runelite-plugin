package com.gielinorspeaks.api.model;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests for ApiException class.
 */
public class ApiExceptionTest {

    @Test
    public void testConstructor() {
        ApiException exception = new ApiException(404, "NOT_FOUND", "Resource not found", false);

        assertEquals(404, exception.getStatusCode());
        assertEquals("NOT_FOUND", exception.getErrorType());
        assertEquals("Resource not found", exception.getMessage());
        assertFalse(exception.isRetryable());
    }

    @Test
    public void testNpcNotFound() {
        ApiException exception = ApiException.npcNotFound(99999);

        assertEquals(404, exception.getStatusCode());
        assertEquals("NPC_NOT_FOUND", exception.getErrorType());
        assertTrue(exception.getMessage().contains("99999"));
        assertFalse(exception.isRetryable());
    }

    @Test
    public void testServiceUnavailable() {
        ApiException exception = ApiException.serviceUnavailable("TTS service offline");

        assertEquals(503, exception.getStatusCode());
        assertEquals("SERVICE_UNAVAILABLE", exception.getErrorType());
        assertEquals("TTS service offline", exception.getMessage());
        assertTrue(exception.isRetryable());
    }

    @Test
    public void testGenerationFailed_retryable() {
        ApiException exception = ApiException.generationFailed("Temporary failure", true);

        assertEquals(500, exception.getStatusCode());
        assertEquals("GENERATION_FAILED", exception.getErrorType());
        assertEquals("Temporary failure", exception.getMessage());
        assertTrue(exception.isRetryable());
    }

    @Test
    public void testGenerationFailed_notRetryable() {
        ApiException exception = ApiException.generationFailed("Invalid voice config", false);

        assertEquals(500, exception.getStatusCode());
        assertEquals("GENERATION_FAILED", exception.getErrorType());
        assertEquals("Invalid voice config", exception.getMessage());
        assertFalse(exception.isRetryable());
    }

    @Test
    public void testNetworkError() {
        IOException cause = new IOException("Connection refused");
        ApiException exception = ApiException.networkError(cause);

        assertEquals(0, exception.getStatusCode());
        assertEquals("NETWORK_ERROR", exception.getErrorType());
        assertTrue(exception.getMessage().contains("Connection refused"));
        assertTrue(exception.isRetryable());
    }
}
