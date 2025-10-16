package com.gielinorspeaks.api;

import com.gielinorspeaks.api.model.ApiException;
import com.gielinorspeaks.api.model.SpeakRequest;
import com.gielinorspeaks.api.model.SpeakResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * Tests for VoiceApiClient using MockWebServer.
 */
public class VoiceApiClientTest {
    private MockWebServer mockServer;
    private VoiceApiClient client;

    @Before
    public void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        // Get base URL without path - we'll add /api/v1 ourselves
        String serverUrl = mockServer.url("").toString();
        // Remove trailing slash from server URL
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
        client = new VoiceApiClient(serverUrl + "/api/v1");
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        mockServer.shutdown();
    }

    @Test
    public void testRequestSpeech_success() throws Exception {
        // Mock response
        String mockResponse = "{"
            + "\"npc_id\": 0,"
            + "\"npc_name\": \"Hans\","
            + "\"text\": \"Hello!\","
            + "\"audio_url\": null,"
            + "\"audio_base64\": \"SGVsbG8=\","
            + "\"cached\": false,"
            + "\"provider\": \"test\","
            + "\"generation_metadata\": {}"
            + "}";

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // Make request
        SpeakRequest request = SpeakRequest.of("Hello!");
        CompletableFuture<SpeakResponse> future = client.requestSpeech(0, request);
        SpeakResponse response = future.get();

        // Verify response
        assertNotNull(response);
        assertEquals(0, response.getNpcId());
        assertEquals("Hans", response.getNpcName());
        assertEquals("Hello!", response.getText());
        assertFalse(response.isCached());
        assertEquals("test", response.getProvider());
        assertTrue(response.hasAudioData());

        // Verify request sent
        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertEquals("/api/v1/npc/0/speak", recordedRequest.getPath());
        assertEquals("POST", recordedRequest.getMethod());
        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("Hello!"));
    }

    @Test
    public void testRequestSpeech_withAnimationId() throws Exception {
        String mockResponse = "{"
            + "\"npc_id\": 741,"
            + "\"npc_name\": \"Duke Horacio\","
            + "\"text\": \"Welcome!\","
            + "\"audio_url\": null,"
            + "\"audio_base64\": \"V2VsY29tZQ==\","
            + "\"cached\": true,"
            + "\"provider\": \"elevenlabs\","
            + "\"generation_metadata\": {}"
            + "}";

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // Make request with animation ID
        SpeakRequest request = SpeakRequest.of("Welcome!", 588);
        CompletableFuture<SpeakResponse> future = client.requestSpeech(741, request);
        SpeakResponse response = future.get();

        assertNotNull(response);
        assertEquals(741, response.getNpcId());
        assertEquals("Duke Horacio", response.getNpcName());
        assertTrue(response.isCached());

        // Verify animation_id was sent
        RecordedRequest recordedRequest = mockServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("animation_id") || body.contains("animationId"));
        assertTrue(body.contains("588"));
    }

    @Test
    public void testRequestSpeech_npcNotFound() throws Exception {
        String errorResponse = "{"
            + "\"detail\": {"
            + "  \"error\": \"NPC_NOT_FOUND\","
            + "  \"message\": \"NPC 99999 does not exist\","
            + "  \"npc_id\": 99999"
            + "}"
            + "}";

        mockServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody(errorResponse)
            .addHeader("Content-Type", "application/json"));

        SpeakRequest request = SpeakRequest.of("test");
        CompletableFuture<SpeakResponse> future = client.requestSpeech(99999, request);

        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ApiException);
            ApiException apiEx = (ApiException) e.getCause();
            assertEquals(404, apiEx.getStatusCode());
            assertEquals("NPC_NOT_FOUND", apiEx.getErrorType());
            assertFalse(apiEx.isRetryable());
        }
    }

    @Test
    public void testRequestSpeech_serviceUnavailable() throws Exception {
        String errorResponse = "{"
            + "\"detail\": {"
            + "  \"error\": \"SERVICE_UNAVAILABLE\","
            + "  \"message\": \"TTS service is offline\","
            + "  \"retryable\": true"
            + "}"
            + "}";

        mockServer.enqueue(new MockResponse()
            .setResponseCode(503)
            .setBody(errorResponse)
            .addHeader("Content-Type", "application/json"));

        SpeakRequest request = SpeakRequest.of("test");
        CompletableFuture<SpeakResponse> future = client.requestSpeech(0, request);

        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ApiException);
            ApiException apiEx = (ApiException) e.getCause();
            assertEquals(503, apiEx.getStatusCode());
            assertEquals("SERVICE_UNAVAILABLE", apiEx.getErrorType());
            assertTrue(apiEx.isRetryable());
        }
    }

    @Test
    public void testRequestSpeech_generationFailed() throws Exception {
        String errorResponse = "{"
            + "\"detail\": {"
            + "  \"error\": \"GENERATION_FAILED\","
            + "  \"message\": \"Voice generation failed\","
            + "  \"retryable\": false"
            + "}"
            + "}";

        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody(errorResponse)
            .addHeader("Content-Type", "application/json"));

        SpeakRequest request = SpeakRequest.of("test");
        CompletableFuture<SpeakResponse> future = client.requestSpeech(0, request);

        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ApiException);
            ApiException apiEx = (ApiException) e.getCause();
            assertEquals(500, apiEx.getStatusCode());
            assertEquals("GENERATION_FAILED", apiEx.getErrorType());
            assertFalse(apiEx.isRetryable());
        }
    }

    @Test
    public void testRequestSpeech_emptyResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("")
            .addHeader("Content-Type", "application/json"));

        SpeakRequest request = SpeakRequest.of("test");
        CompletableFuture<SpeakResponse> future = client.requestSpeech(0, request);

        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ApiException);
            ApiException apiEx = (ApiException) e.getCause();
            assertEquals("EMPTY_RESPONSE", apiEx.getErrorType());
        }
    }

    @Test
    public void testRequestSpeech_invalidJson() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("not json")
            .addHeader("Content-Type", "application/json"));

        SpeakRequest request = SpeakRequest.of("test");
        CompletableFuture<SpeakResponse> future = client.requestSpeech(0, request);

        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ApiException);
            ApiException apiEx = (ApiException) e.getCause();
            assertEquals("PARSE_ERROR", apiEx.getErrorType());
        }
    }

    @Test
    public void testCheckHealth_success() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"status\": \"healthy\"}"));

        CompletableFuture<Boolean> future = client.checkHealth();
        assertTrue(future.get());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("/api/v1/health", request.getPath());
        assertEquals("GET", request.getMethod());
    }

    @Test
    public void testCheckHealth_failure() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(503));

        CompletableFuture<Boolean> future = client.checkHealth();
        assertFalse(future.get());
    }

    @Test
    public void testGetBaseUrl() {
        String serverUrl = mockServer.url("").toString();
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
        String baseUrl = serverUrl + "/api/v1";
        VoiceApiClient testClient = new VoiceApiClient(baseUrl);

        assertEquals(baseUrl, testClient.getBaseUrl());
        testClient.shutdown();
    }
}
