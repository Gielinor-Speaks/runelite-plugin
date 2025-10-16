package com.gielinorspeaks.api.model;

import lombok.Getter;

/**
 * Exception thrown when API requests fail.
 * Contains HTTP status code, error type, and retry information.
 */
@Getter
public class
ApiException extends Exception {
    private final int statusCode;
    private final String errorType;
    private final boolean retryable;

    /**
     * Create an API exception.
     *
     * @param statusCode HTTP status code
     * @param errorType Error type identifier
     * @param message Human-readable error message
     * @param retryable Whether the request can be retried
     */
    public ApiException(int statusCode, String errorType, String message, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.retryable = retryable;
    }

    /**
     * Create an exception for NPC not found errors.
     *
     * @param npcId The NPC ID that was not found
     * @return A new ApiException
     */
    public static ApiException npcNotFound(int npcId) {
        return new ApiException(
            404,
            "NPC_NOT_FOUND",
            "NPC " + npcId + " does not exist",
            false
        );
    }

    /**
     * Create an exception for service unavailable errors.
     *
     * @param reason The reason for service unavailability
     * @return A new ApiException
     */
    public static ApiException serviceUnavailable(String reason) {
        return new ApiException(
            503,
            "SERVICE_UNAVAILABLE",
            reason,
            true
        );
    }

    /**
     * Create an exception for generation failures.
     *
     * @param reason The reason for generation failure
     * @param retryable Whether the request can be retried
     * @return A new ApiException
     */
    public static ApiException generationFailed(String reason, boolean retryable) {
        return new ApiException(
            500,
            "GENERATION_FAILED",
            reason,
            retryable
        );
    }

    /**
     * Create an exception for network/connection errors.
     *
     * @param cause The underlying IO exception
     * @return A new ApiException
     */
    public static ApiException networkError(Throwable cause) {
        return new ApiException(
            0,
            "NETWORK_ERROR",
            "Network error: " + cause.getMessage(),
            true
        );
    }
}
