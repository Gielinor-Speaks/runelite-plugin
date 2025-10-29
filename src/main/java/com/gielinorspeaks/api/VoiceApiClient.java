package com.gielinorspeaks.api;

import com.gielinorspeaks.api.model.ApiException;
import com.gielinorspeaks.api.model.SpeakRequest;
import com.gielinorspeaks.api.model.SpeakResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for voiceover-mage REST API.
 * Provides async methods for requesting NPC speech generation.
 */
@Slf4j
@Singleton
public class VoiceApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;

    /**
     * Create a VoiceApiClient with custom base URL.
     *
     * @param baseUrl The base URL for the voiceover-mage API
     */
    public VoiceApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.gson = new com.google.gson.GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Request speech generation for an NPC.
     * Makes async POST request to /npc/{npc_id}/speak endpoint.
     *
     * @param npcId NPC identifier
     * @param request SpeakRequest containing text and optional animation ID
     * @return CompletableFuture with SpeakResponse containing audio data
     */
    public CompletableFuture<SpeakResponse> requestSpeech(int npcId, SpeakRequest request) {
        CompletableFuture<SpeakResponse> future = new CompletableFuture<>();

        // Build request
        String json = gson.toJson(request);
        RequestBody body = RequestBody.create(json, JSON);

        String url = baseUrl + "/npc/" + npcId + "/speak";
        Request httpRequest = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        log.debug("Requesting speech for NPC {} with text: '{}' (animationId: {})",
            npcId, request.getText(), request.getAnimationId());

        // Execute async
        httpClient.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("API request failed for NPC {}", npcId, e);
                future.completeExceptionally(
                    ApiException.networkError(e)
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        future.completeExceptionally(
                            new ApiException(response.code(), "EMPTY_RESPONSE", "Empty response from API", false)
                        );
                        return;
                    }

                    String responseJson = responseBody.string();

                    // Check for empty response body
                    if (responseJson == null || responseJson.trim().isEmpty()) {
                        future.completeExceptionally(
                            new ApiException(response.code(), "EMPTY_RESPONSE", "Empty response body from API", false)
                        );
                        return;
                    }

                    if (!response.isSuccessful()) {
                        handleErrorResponse(response.code(), responseJson, future);
                        return;
                    }

                    // Parse success response
                    SpeakResponse speakResponse = gson.fromJson(responseJson, SpeakResponse.class);
                    log.info("Speech received for NPC {} (cached: {}, {} bytes)",
                        npcId, speakResponse.isCached(),
                        speakResponse.hasAudioData() ? speakResponse.decodeAudio().length : 0);
                    future.complete(speakResponse);

                } catch (Exception e) {
                    log.error("Failed to parse API response for NPC {}", npcId, e);
                    future.completeExceptionally(
                        new ApiException(500, "PARSE_ERROR", "Failed to parse response: " + e.getMessage(), false)
                    );
                }
            }
        });

        return future;
    }

    /**
     * Check API health status.
     * Makes async GET request to /health endpoint.
     *
     * @return CompletableFuture<Boolean> true if API is healthy
     */
    public CompletableFuture<Boolean> checkHealth() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String url = baseUrl + "/health";
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Health check failed", e);
                future.complete(false);
            }

            @Override
            public void onResponse(Call call, Response response) {
                boolean healthy = response.isSuccessful();
                log.debug("Health check: {}", healthy ? "healthy" : "unhealthy");
                future.complete(healthy);
                response.close();
            }
        });

        return future;
    }

    /**
     * Handle error response from API.
     * Parses error JSON and creates appropriate ApiException.
     *
     * @param statusCode HTTP status code
     * @param responseJson Response body JSON
     * @param future Future to complete exceptionally
     */
    private void handleErrorResponse(int statusCode, String responseJson, CompletableFuture<SpeakResponse> future) {
        try {
            JsonObject errorObj = gson.fromJson(responseJson, JsonObject.class);
            JsonObject detail = errorObj.has("detail") ? errorObj.getAsJsonObject("detail") : errorObj;

            String errorType = detail.has("error") ? detail.get("error").getAsString() : "UNKNOWN_ERROR";
            String message = detail.has("message") ? detail.get("message").getAsString() : "Unknown error";
            boolean retryable = detail.has("retryable") && detail.get("retryable").getAsBoolean();

            log.warn("API error: {} - {} (retryable: {})", errorType, message, retryable);
            future.completeExceptionally(new ApiException(statusCode, errorType, message, retryable));

        } catch (Exception e) {
            log.error("Failed to parse error response", e);
            future.completeExceptionally(
                new ApiException(statusCode, "PARSE_ERROR", "Failed to parse error: " + responseJson, false)
            );
        }
    }

    /**
     * Get the base URL configured for this client.
     *
     * @return The base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Shutdown the HTTP client and cleanup resources.
     * Should be called when the plugin shuts down.
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
