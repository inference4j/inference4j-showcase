package net.inference4j.showcase;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.nlp.MarianTranslator;

import jakarta.annotation.PreDestroy;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/translation")
public class TranslationController {

	private static final Set<String> KNOWN_TARGETS = Set.of("fr", "de", "es");

	private final ModelCache cache;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public TranslationController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter translate(@RequestBody TranslateRequest request) {
		String target = request.target() != null ? request.target() : "fr";
		if (!KNOWN_TARGETS.contains(target)) {
			throw new IllegalArgumentException("Unknown target language: " + target);
		}

		SseEmitter emitter = new SseEmitter(300_000L);

		executor.submit(() -> {
			try {
				String key = "opus-mt-en-" + target;
				MarianTranslator translator = cache.get(key,
					() -> MarianTranslator.builder()
						.modelId("inference4j/opus-mt-en-" + target)
						.maxNewTokens(200)
						.build());
				GenerationResult result = translator.translate(request.text(), token -> {
					try {
						emitter.send(SseEmitter.event().name("token").data(token));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
				emitter.send(SseEmitter.event().name("done").data(
					Map.of("tokenCount", result.generatedTokens(),
						   "durationMillis", result.duration().toMillis())
				));
				emitter.complete();
			} catch (Exception e) {
				try {
					emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
				} catch (Exception ignored) {
				}
				emitter.completeWithError(e);
			}
		});

		return emitter;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	record TranslateRequest(String text, String target) {}

}
