package net.inference4j.showcase;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.nlp.Gpt2TextGenerator;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/gpt2")
public class Gpt2Controller {

	private final ObjectProvider<Gpt2TextGenerator> generatorProvider;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public Gpt2Controller(ObjectProvider<Gpt2TextGenerator> generatorProvider) {
		this.generatorProvider = generatorProvider;
	}

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter generate(@RequestBody GenerateRequest request) {
		Gpt2TextGenerator generator = generatorProvider.getObject();
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

	record GenerateRequest(String prompt) {}

}
