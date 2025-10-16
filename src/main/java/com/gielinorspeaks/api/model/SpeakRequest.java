package com.gielinorspeaks.api.model;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

import javax.annotation.Nullable;

/**
 * Request model for /npc/{npc_id}/speak endpoint.
 */
@Value
public class SpeakRequest {
    @SerializedName("text")
    String text;

    /**
     * Optional animation ID for emotion mapping.
     * When provided, the API will map the animation to an emotion vector for voice synthesis.
     */
    @Nullable
    @SerializedName("animation_id")
    Integer animationId;

    /**
     * Create a speak request with only dialogue text.
     *
     * @param text The dialogue text to synthesize
     * @return A new SpeakRequest instance
     */
    public static SpeakRequest of(String text) {
        return new SpeakRequest(text, null);
    }

    /**
     * Create a speak request with dialogue text and animation ID.
     *
     * @param text The dialogue text to synthesize
     * @param animationId The animation ID for emotion mapping
     * @return A new SpeakRequest instance
     */
    public static SpeakRequest of(String text, Integer animationId) {
        return new SpeakRequest(text, animationId);
    }
}
