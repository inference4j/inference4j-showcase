package net.inference4j.showcase;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;

import io.github.inference4j.audio.MoonshineRecognizer;
import io.github.inference4j.audio.SileroVadDetector;
import io.github.inference4j.audio.TranscriptLine;
import io.github.inference4j.audio.TranscriptListener;
import io.github.inference4j.audio.TranscriptionStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Live transcription over a WebSocket.
 *
 * <p>The browser connects to {@code /ws/live-transcription?model=small&sampleRate=48000} and sends the
 * microphone as binary messages of little-endian 32-bit float samples, mono, at {@code sampleRate}. The
 * text message {@code stop} completes the open line. The server answers with JSON text messages:
 * <ul>
 *   <li>{@code {"type":"ready"}} once the model is loaded;</li>
 *   <li>{@code {"type":"started"|"changed"|"completed","id":..,"text":..,"start":..,"end":..,"latencyMs":..}}
 *       for each line event, with times in seconds on the stream's timeline and {@code latencyMs} the
 *       time from receiving the audio message that produced the event to sending the event;</li>
 *   <li>{@code {"type":"error","message":..}} if something fails.</li>
 * </ul>
 */
@Component
public class LiveTranscriptionHandler extends AbstractWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(LiveTranscriptionHandler.class);

	private static final Map<String, String> MODELS = Map.of(
			"tiny", "inference4j/moonshine-streaming-tiny-en",
			"small", "inference4j/moonshine-streaming-small-en");

	private static final String STREAM = "stream";
	private static final String SAMPLE_RATE = "sampleRate";
	private static final String MODEL = "model";
	private static final String TIMING = "timing";

	private final ModelCache cache;

	public LiveTranscriptionHandler(ModelCache cache) {
		this.cache = cache;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
		// Listener events and errors are sent from the same thread today, but the decorator keeps sends safe
		WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, 5_000, 1 << 20);
		URI uri = rawSession.getUri();
		Map<String, String> query = uri == null ? Map.of()
				: UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
		String model = query.getOrDefault(MODEL, "small");
		if (!MODELS.containsKey(model)) {
			sendError(session, "Unknown model: " + model);
			rawSession.close(CloseStatus.BAD_DATA);
			return;
		}
		int sampleRate = Integer.parseInt(query.getOrDefault(SAMPLE_RATE, "16000"));

		// Start of the current addAudio call, so events can report how long they took to produce
		long[] timing = new long[1];
		MoonshineRecognizer recognizer = recognizer(model);
		TranscriptionStream stream = recognizer.stream(vad(model))
			.listener(new TranscriptListener() {
				@Override
				public void onLineStarted(TranscriptLine line) {
					send(session, "started", line, sinceMillis(timing[0]));
				}

				@Override
				public void onLineTextChanged(TranscriptLine line) {
					send(session, "changed", line, sinceMillis(timing[0]));
				}

				@Override
				public void onLineCompleted(TranscriptLine line) {
					send(session, "completed", line, sinceMillis(timing[0]));
				}
			})
			.build();

		rawSession.getAttributes().put(STREAM, stream);
		rawSession.getAttributes().put(SAMPLE_RATE, sampleRate);
		rawSession.getAttributes().put(MODEL, model);
		rawSession.getAttributes().put(TIMING, timing);
		rawSession.getAttributes().put("decorated", session);
		session.sendMessage(new TextMessage("{\"type\":\"ready\"}"));
		log.info("Live transcription started: model={}, sampleRate={}", model, sampleRate);
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession rawSession, BinaryMessage message) {
		TranscriptionStream stream = (TranscriptionStream) rawSession.getAttributes().get(STREAM);
		if (stream == null) {
			return;
		}
		// Keeps the cached models from being evicted while the stream is in use
		String model = (String) rawSession.getAttributes().get(MODEL);
		recognizer(model);
		vad(model);

		FloatBuffer floats = message.getPayload().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
		float[] samples = new float[floats.remaining()];
		floats.get(samples);

		long[] timing = (long[]) rawSession.getAttributes().get(TIMING);
		timing[0] = System.nanoTime();
		try {
			stream.addAudio(samples, (Integer) rawSession.getAttributes().get(SAMPLE_RATE));
		}
		catch (RuntimeException e) {
			log.warn("Live transcription failed", e);
			sendError(decorated(rawSession), e.getMessage());
		}
	}

	private static long sinceMillis(long startNanos) {
		return startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1_000_000;
	}

	@Override
	protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
		TranscriptionStream stream = (TranscriptionStream) rawSession.getAttributes().get(STREAM);
		if (stream != null && "stop".equals(message.getPayload().trim())) {
			long[] timing = (long[]) rawSession.getAttributes().get(TIMING);
			timing[0] = System.nanoTime();
			stream.flush();
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession rawSession, CloseStatus status) {
		TranscriptionStream stream = (TranscriptionStream) rawSession.getAttributes().remove(STREAM);
		if (stream != null) {
			try {
				stream.close();
			}
			catch (RuntimeException e) {
				log.debug("Error closing stream", e);
			}
		}
		log.info("Live transcription ended: {}", status);
	}

	private MoonshineRecognizer recognizer(String model) {
		return cache.get("moonshine-" + model, () -> MoonshineRecognizer.builder().modelId(MODELS.get(model)).build());
	}

	/** One detector per model: streams of one recognizer are serialized, so they can share it safely. */
	private SileroVadDetector vad(String model) {
		return cache.get("silero-vad-live-" + model, () -> SileroVadDetector.builder().build());
	}

	private static WebSocketSession decorated(WebSocketSession rawSession) {
		return (WebSocketSession) rawSession.getAttributes().get("decorated");
	}

	private static void send(WebSocketSession session, String type, TranscriptLine line, long latencyMs) {
		String json = "{\"type\":\"" + type + "\",\"id\":" + line.id() + ",\"text\":" + quote(line.text())
				+ ",\"start\":" + seconds(line.start().toMillis()) + ",\"end\":" + seconds(line.end().toMillis())
				+ ",\"latencyMs\":" + latencyMs + "}";
		try {
			if (session.isOpen()) {
				session.sendMessage(new TextMessage(json));
			}
		}
		catch (IOException e) {
			log.debug("Could not send event", e);
		}
	}

	private static void sendError(WebSocketSession session, String message) {
		try {
			if (session != null && session.isOpen()) {
				session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":" + quote(String.valueOf(message)) + "}"));
			}
		}
		catch (IOException e) {
			log.debug("Could not send error", e);
		}
	}

	private static String seconds(long millis) {
		return String.format(java.util.Locale.ROOT, "%.2f", millis / 1000.0);
	}

	private static String quote(String text) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : text.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					}
					else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}

}
