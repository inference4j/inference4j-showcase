package net.inference4j.showcase;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.genai.GenerationResult;
import io.github.inference4j.nlp.TextGenerator;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/text-generation")
public class TextGenerationController {

	private final Map<String, ObjectProvider<TextGenerator>> generators;
	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	public TextGenerationController(
			@Qualifier("phi3Generator") ObjectProvider<TextGenerator> phi3,
			@Qualifier("deepSeekGenerator") ObjectProvider<TextGenerator> deepSeek) {
		this.generators = Map.of(
			"phi3", phi3,
			"deepseek", deepSeek
		);
	}

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter generate(@RequestBody GenerateRequest request) {
		String model = request.model();
		ObjectProvider<TextGenerator> provider = generators.get(model);
		if (provider == null) {
			throw new IllegalArgumentException("Unknown model: " + model);
		}

		TextGenerator generator = provider.getObject();
		SseEmitter emitter = new SseEmitter(300_000L);

		executor.submit(() -> {
			try {
				GenerationResult result = generator.generate(request.prompt(), token -> {
					try {
						emitter.send(SseEmitter.event().name("token").data(token));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
				emitter.send(SseEmitter.event().name("done").data(
					Map.of("tokenCount", result.tokenCount(),
						   "durationMillis", result.durationMillis())
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

	record GenerateRequest(String prompt, String model) {}

}
