package com.gielinorspeaks.service;

import com.gielinorspeaks.api.VoiceApiClient;
import com.gielinorspeaks.api.model.ApiException;
import com.gielinorspeaks.api.model.SpeakRequest;
import com.gielinorspeaks.api.model.SpeakResponse;
import com.gielinorspeaks.audio.AudioPlayer;
import com.gielinorspeaks.model.DialogueEvent;
import com.gielinorspeaks.model.DialogueSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for VoiceOrchestrationService.
 */
public class VoiceOrchestrationServiceTest {
	private VoiceApiClient mockApiClient;
	private AudioPlayer mockAudioPlayer;
	private VoiceOrchestrationService service;

	@Before
	public void setUp() {
		mockApiClient = mock(VoiceApiClient.class);
		mockAudioPlayer = mock(AudioPlayer.class);
		service = new VoiceOrchestrationService(mockApiClient, mockAudioPlayer);
	}

	@Test
	public void testHandleDialogue_success() {
		// Create test event
		DialogueEvent event = new DialogueEvent(
			0,
			"Hans",
			"Hello, adventurer!",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		// Mock API response
		SpeakResponse mockResponse = new SpeakResponse(
			0,
			"Hans",
			"Hello, adventurer!",
			null,
			"dGVzdCBhdWRpbw==", // "test audio" in base64
			false,
			"test-provider",
			new HashMap<>()
		);

		CompletableFuture<SpeakResponse> future = CompletableFuture.completedFuture(mockResponse);
		when(mockApiClient.requestSpeech(eq(0), any(SpeakRequest.class))).thenReturn(future);

		// Handle dialogue
		service.handleDialogue(event);

		// Verify API was called
		verify(mockApiClient).requestSpeech(eq(0), any(SpeakRequest.class));

		// Wait for async completion
		sleep(100);

		// Verify audio was played for the correct NPC
		ArgumentCaptor<Integer> npcIdCaptor = ArgumentCaptor.forClass(Integer.class);
		ArgumentCaptor<byte[]> audioCaptor = ArgumentCaptor.forClass(byte[].class);
		verify(mockAudioPlayer).play(npcIdCaptor.capture(), audioCaptor.capture());

		assertEquals(Integer.valueOf(0), npcIdCaptor.getValue());
		byte[] capturedAudio = audioCaptor.getValue();
		assertNotNull(capturedAudio);
		assertTrue(capturedAudio.length > 0);
	}

	@Test
	public void testHandleDialogue_withAnimationId() {
		DialogueEvent event = new DialogueEvent(
			741,
			"Duke Horacio",
			"Welcome!",
			DialogueSource.DIALOGUE_BOX,
			588 // Animation ID
		);

		SpeakResponse mockResponse = new SpeakResponse(
			741,
			"Duke Horacio",
			"Welcome!",
			null,
			"dGVzdA==",
			false,
			"test-provider",
			new HashMap<>()
		);

		CompletableFuture<SpeakResponse> future = CompletableFuture.completedFuture(mockResponse);
		when(mockApiClient.requestSpeech(eq(741), any(SpeakRequest.class))).thenReturn(future);

		service.handleDialogue(event);
		sleep(100);

		// Verify API was called with correct NPC ID
		ArgumentCaptor<SpeakRequest> requestCaptor = ArgumentCaptor.forClass(SpeakRequest.class);
		verify(mockApiClient).requestSpeech(eq(741), requestCaptor.capture());

		// Verify animation ID was included
		SpeakRequest capturedRequest = requestCaptor.getValue();
		assertEquals("Welcome!", capturedRequest.getText());
		assertEquals(Integer.valueOf(588), capturedRequest.getAnimationId());

		// Verify audio was played for the correct NPC
		verify(mockAudioPlayer).play(eq(741), any(byte[].class));
	}

	@Test
	public void testHandleDialogue_noAudioData() {
		DialogueEvent event = new DialogueEvent(
			0,
			"Hans",
			"Hello!",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		// Mock response with no audio data
		SpeakResponse mockResponse = new SpeakResponse(
			0,
			"Hans",
			"Hello!",
			null,
			null, // No audio
			false,
			"test-provider",
			new HashMap<>()
		);

		CompletableFuture<SpeakResponse> future = CompletableFuture.completedFuture(mockResponse);
		when(mockApiClient.requestSpeech(eq(0), any(SpeakRequest.class))).thenReturn(future);

		service.handleDialogue(event);
		sleep(100);

		// Verify audio was NOT played
		verify(mockAudioPlayer, never()).play(anyInt(), any(byte[].class));
	}

	@Test
	public void testHandleDialogue_apiError_notRetryable() {
		DialogueEvent event = new DialogueEvent(
			99999,
			"Unknown",
			"Test",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		// Mock API error
		ApiException apiError = ApiException.npcNotFound(99999);
		CompletableFuture<SpeakResponse> future = new CompletableFuture<>();
		future.completeExceptionally(apiError);

		when(mockApiClient.requestSpeech(eq(99999), any(SpeakRequest.class))).thenReturn(future);

		service.handleDialogue(event);
		sleep(100);

		// Verify audio was not played
		verify(mockAudioPlayer, never()).play(anyInt(), any(byte[].class));
	}

	@Test
	public void testHandleDialogue_apiError_retryable() {
		DialogueEvent event = new DialogueEvent(
			0,
			"Hans",
			"Test",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		// Mock retryable API error
		ApiException apiError = ApiException.serviceUnavailable("TTS service offline");
		CompletableFuture<SpeakResponse> future = new CompletableFuture<>();
		future.completeExceptionally(apiError);

		when(mockApiClient.requestSpeech(eq(0), any(SpeakRequest.class))).thenReturn(future);

		service.handleDialogue(event);
		sleep(100);

		// Verify audio was not played
		verify(mockAudioPlayer, never()).play(anyInt(), any(byte[].class));
	}

	@Test
	public void testHandleDialogue_multipleNpcs() {
		DialogueEvent event1 = new DialogueEvent(
			0,
			"Hans",
			"First dialogue",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		DialogueEvent event2 = new DialogueEvent(
			1,
			"Cook",
			"Second dialogue",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		SpeakResponse mockResponse1 = new SpeakResponse(
			0,
			"Hans",
			"Test",
			null,
			"dGVzdA==",
			false,
			"test",
			new HashMap<>()
		);

		SpeakResponse mockResponse2 = new SpeakResponse(
			1,
			"Cook",
			"Test",
			null,
			"dGVzdA==",
			false,
			"test",
			new HashMap<>()
		);

		when(mockApiClient.requestSpeech(eq(0), any(SpeakRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(mockResponse1));
		when(mockApiClient.requestSpeech(eq(1), any(SpeakRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(mockResponse2));

		// Handle first dialogue
		service.handleDialogue(event1);
		sleep(100);

		// Handle second dialogue
		service.handleDialogue(event2);
		sleep(100);

		// Verify both NPCs got audio played
		verify(mockAudioPlayer).play(eq(0), any(byte[].class));
		verify(mockAudioPlayer).play(eq(1), any(byte[].class));
	}

	@Test
	public void testHandleDialogueEnd_stopsMatchingNpc() {
		when(mockAudioPlayer.isPlaying(0)).thenReturn(true);

		// Simulate dialogue being played
		DialogueEvent event = new DialogueEvent(
			0,
			"Hans",
			"Test",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		SpeakResponse mockResponse = new SpeakResponse(
			0,
			"Hans",
			"Test",
			null,
			"dGVzdA==",
			false,
			"test",
			new HashMap<>()
		);

		when(mockApiClient.requestSpeech(eq(0), any(SpeakRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(mockResponse));

		service.handleDialogue(event);
		sleep(100);

		// Now end dialogue for same NPC
		service.handleDialogueEnd(0);

		// Verify stop was called for the correct NPC
		verify(mockAudioPlayer).stop(eq(0));
	}

	@Test
	public void testHandleDialogueEnd_differentNpc() {
		// Simulate NPC 0 playing
		DialogueEvent event = new DialogueEvent(
			0,
			"Hans",
			"Test",
			DialogueSource.DIALOGUE_BOX,
			null
		);

		SpeakResponse mockResponse = new SpeakResponse(
			0,
			"Hans",
			"Test",
			null,
			"dGVzdA==",
			false,
			"test",
			new HashMap<>()
		);

		when(mockApiClient.requestSpeech(eq(0), any(SpeakRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(mockResponse));

		service.handleDialogue(event);
		sleep(100);

		reset(mockAudioPlayer); // Reset to clear previous calls

		// End dialogue for different NPC
		service.handleDialogueEnd(1);

		// Verify stop was called for NPC 1, not NPC 0
		verify(mockAudioPlayer).stop(eq(1));
		verify(mockAudioPlayer, never()).stop(eq(0));
	}

	@Test
	public void testShutdown() {
		service.shutdown();

		// Verify cleanup
		verify(mockAudioPlayer).shutdown();
		verify(mockApiClient).shutdown();
	}

	@Test
	public void testIsPlaying() {
		int npcId = 0;
		when(mockAudioPlayer.isPlaying(npcId)).thenReturn(true);
		assertTrue(service.isPlaying(npcId));

		when(mockAudioPlayer.isPlaying(npcId)).thenReturn(false);
		assertFalse(service.isPlaying(npcId));
	}

	@Test
	public void testIsAnyPlaying() {
		when(mockAudioPlayer.isAnyPlaying()).thenReturn(true);
		assertTrue(service.isAnyPlaying());

		when(mockAudioPlayer.isAnyPlaying()).thenReturn(false);
		assertFalse(service.isAnyPlaying());
	}

	@Test
	public void testGetActiveStreamCount() {
		when(mockAudioPlayer.getActiveStreamCount()).thenReturn(3);
		assertEquals(3, service.getActiveStreamCount());

		when(mockAudioPlayer.getActiveStreamCount()).thenReturn(0);
		assertEquals(0, service.getActiveStreamCount());
	}

	/**
	 * Helper method to sleep and allow async operations to complete.
	 */
	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
