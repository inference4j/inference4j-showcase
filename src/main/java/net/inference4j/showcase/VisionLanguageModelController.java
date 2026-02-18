package net.inference4j.showcase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.genai.GenerationResult;
import io.github.inference4j.vision.VisionInput;
import io.github.inference4j.vision.VisionLanguageModel;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/vision-language")
public class VisionLanguageModelController {

	private final ObjectProvider<VisionLanguageModel> modelProvider;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public VisionLanguageModelController(ObjectProvider<VisionLanguageModel> modelProvider) {
		this.modelProvider = modelProvider;
	}

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter generate(
			@RequestParam("image") MultipartFile file,
			@RequestParam("prompt") String prompt) throws IOException {

		if (file.isEmpty()) {
			throw new IllegalArgumentException("Image file is required");
		}
		if (prompt == null || prompt.isBlank()) {
			throw new IllegalArgumentException("Prompt is required");
		}

		String originalName = file.getOriginalFilename();
		String suffix = originalName != null && originalName.contains(".")
				? originalName.substring(originalName.lastIndexOf('.'))
				: ".jpg";
		Path tempFile = Files.createTempFile("vision-", suffix);
		file.transferTo(tempFile.toFile());

		VisionLanguageModel model = modelProvider.getObject();
		SseEmitter emitter = new SseEmitter(300_000L);

		executor.submit(() -> {
			try {
				GenerationResult result = model.generate(
						new VisionInput(tempFile, prompt), token -> {
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
			} finally {
				try {
					Files.deleteIfExists(tempFile);
				} catch (IOException ignored) {
				}
			}
		});

		return emitter;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

}
