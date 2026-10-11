package net.inference4j.showcase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.inference4j.audio.PunctuatedSpeechRecognizer;
import io.github.inference4j.audio.SpeechRecognizer;
import io.github.inference4j.audio.Wav2Vec2Recognizer;
import io.github.inference4j.genai.audio.WhisperSpeechModel;
import io.github.inference4j.nlp.PunctCapSegModel;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/speech-to-text")
public class SpeechToTextController {

	private final ModelCache cache;

	public SpeechToTextController(ModelCache cache) {
		this.cache = cache;
	}

	/**
	 * @param text           the transcript returned to the user
	 * @param raw            the recognizer's unformatted output, set only when punctuation was applied
	 * @param durationMillis time spent producing {@code text}
	 */
	public record SpeechToTextResponse(String text, String raw, long durationMillis) {
	}

	/**
	 * @param model {@code wav2vec2}, {@code wav2vec2-punctuated} or {@code whisper}
	 */
	@PostMapping
	public SpeechToTextResponse transcribe(@RequestParam("audio") MultipartFile file,
			@RequestParam(name = "model", defaultValue = "wav2vec2") String model) throws IOException {
		var tmp = Files.createTempFile("speech-", ".wav");
		try {
			file.transferTo(tmp);
			return switch (model) {
				case "wav2vec2" -> timed(wav2vec2(), tmp, null);
				case "wav2vec2-punctuated" -> punctuated(tmp);
				case "whisper" -> whisper(tmp);
				default -> throw new IllegalArgumentException("Unknown model: " + model);
			};
		}
		finally {
			Files.deleteIfExists(tmp);
		}
	}

	private SpeechToTextResponse punctuated(Path audio) {
		SpeechRecognizer recognizer = wav2vec2();
		// stride lets transcripts longer than the model's 256-token window be formatted in full
		PunctCapSegModel punctuation = cache.get("punct-cap-seg",
				() -> PunctCapSegModel.builder().stride(64).build());
		// The cache owns both models, so this per-request decorator is deliberately not closed:
		// closing it would close the cached recognizer and punctuation model.
		var punctuated = new PunctuatedSpeechRecognizer(recognizer, punctuation);
		String raw = recognizer.transcribe(audio).text();
		return timed(punctuated, audio, raw);
	}

	private SpeechToTextResponse whisper(Path audio) {
		WhisperSpeechModel whisper = cache.get("whisper-small",
				() -> WhisperSpeechModel.builder()
					.modelId("inference4j/whisper-small-genai")
					.build());
		long start = System.nanoTime();
		String text = whisper.transcribe(audio).text();
		return new SpeechToTextResponse(text, null, (System.nanoTime() - start) / 1_000_000);
	}

	private SpeechRecognizer wav2vec2() {
		return cache.get("wav2vec2", () -> Wav2Vec2Recognizer.builder().build());
	}

	private static SpeechToTextResponse timed(SpeechRecognizer recognizer, Path audio, String raw) {
		long start = System.nanoTime();
		String text = recognizer.transcribe(audio).text();
		return new SpeechToTextResponse(text, raw, (System.nanoTime() - start) / 1_000_000);
	}

}
