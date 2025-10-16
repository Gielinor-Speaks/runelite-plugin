package com.gielinorspeaks.api.model;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Map;

/**
 * Response model from /npc/{npc_id}/speak endpoint.
 * Contains audio data and metadata about the generated speech.
 */
@Value
public class SpeakResponse {
    @SerializedName("npc_id")
    int npcId;

    @SerializedName("npc_name")
    String npcName;

    @SerializedName("text")
    String text;

    @Nullable
    @SerializedName("audio_url")
    String audioUrl;

    @Nullable
    @SerializedName("audio_base64")
    String audioBase64;

    @SerializedName("cached")
    boolean cached;

    @SerializedName("provider")
    String provider;

    @SerializedName("generation_metadata")
    Map<String, Object> generationMetadata;

    /**
     * Decode base64 audio data to bytes.
     *
     * @return Audio bytes (MP3 format), or null if no base64 data present
     */
    @Nullable
    public byte[] decodeAudio() {
        if (audioBase64 == null || audioBase64.isEmpty()) {
            return null;
        }
        return Base64.getDecoder().decode(audioBase64);
    }

    /**
     * Check if audio data is available in the response.
     *
     * @return true if audio data is present
     */
    public boolean hasAudioData() {
        return audioBase64 != null && !audioBase64.isEmpty();
    }
}
