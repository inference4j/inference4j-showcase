package net.inference4j.showcase;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.nlp.BartSummarizer;

import jakarta.annotation.PreDestroy;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/summarization")
public class SummarizationController {

	private static final Set<String> KNOWN_MODELS = Set.of("distilbart-cnn", "bart-large-cnn");

	private final ModelCache cache;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public SummarizationController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter summarize(@RequestBody SummarizeRequest request) {
		String model = request.model() != null ? request.model() : "distilbart-cnn";
		if (!KNOWN_MODELS.contains(model)) {
			throw new IllegalArgumentException("Unknown model: " + model);
		}

		SseEmitter emitter = new SseEmitter(300_000L);

		executor.submit(() -> {
			try {
				BartSummarizer summarizer = cache.get(model, () -> buildSummarizer(model));
				GenerationResult result = summarizer.summarize(request.text(), token -> {
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

	private BartSummarizer buildSummarizer(String model) {
		BartSummarizer.Builder builder = switch (model) {
			case "bart-large-cnn" -> BartSummarizer.bartLargeCnn();
			default -> BartSummarizer.distilBartCnn();
		};
		return builder.maxNewTokens(150).build();
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	record SummarizeRequest(String text, String model) {}

}
