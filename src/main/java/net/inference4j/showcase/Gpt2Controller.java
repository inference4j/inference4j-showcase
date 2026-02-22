package net.inference4j.showcase;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.nlp.OnnxTextGenerator;

import jakarta.annotation.PreDestroy;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/gpt2")
public class Gpt2Controller {

	private static final Set<String> KNOWN_MODELS = Set.of("gpt2", "smollm2", "smollm2-1.7b", "qwen2");

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter generate(@RequestBody GenerateRequest request) {
		String model = request.model() != null ? request.model() : "gpt2";
		if (!KNOWN_MODELS.contains(model)) {
			throw new IllegalArgumentException("Unknown model: " + model);
		}

		SseEmitter emitter = new SseEmitter(300_000L);

		executor.submit(() -> {
			try (OnnxTextGenerator generator = buildGenerator(model)) {
				GenerationResult result = generator.generate(request.prompt(), token -> {
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

	private OnnxTextGenerator buildGenerator(String model) {
		OnnxTextGenerator.Builder builder = switch (model) {
			case "smollm2" -> OnnxTextGenerator.smolLM2();
			case "smollm2-1.7b" -> OnnxTextGenerator.smolLM2_1_7B();
			case "qwen2" -> OnnxTextGenerator.qwen2();
			default -> OnnxTextGenerator.gpt2();
		};
		return builder
			.maxNewTokens(256)
			.temperature(0.8f)
			.topK(50)
			.topP(0.9f)
			.build();
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	record GenerateRequest(String prompt, String model) {}

}
