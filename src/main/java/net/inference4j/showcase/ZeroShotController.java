package net.inference4j.showcase;

import java.util.List;

import io.github.inference4j.nlp.DebertaZeroShotClassifier;
import io.github.inference4j.nlp.TextClassification;
import io.github.inference4j.nlp.ZeroShotTextClassifier;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/zero-shot")
public class ZeroShotController {

	private final ModelCache cache;

	public ZeroShotController(ModelCache cache) {
		this.cache = cache;
	}

	record ZeroShotRequest(String text, List<String> labels, String model, boolean multiLabel) {
	}

	record ZeroShotResponse(List<TextClassification> results, long millis, String model, String template) {
	}

	@PostMapping
	public ZeroShotResponse classify(@RequestBody ZeroShotRequest request) {
		List<String> labels = request.labels() == null ? List.of()
				: request.labels().stream().map(String::strip).filter(l -> !l.isEmpty()).distinct().toList();
		if (request.text() == null || request.text().isBlank() || labels.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a text and at least one label");
		}
		boolean large = "large".equals(request.model());
		// The template and the label mode are fixed per classifier, and each classifier loads its own model:
		// the page offers two models and two modes, so at most four are cached
		ZeroShotTextClassifier classifier = cache.get(
				"zero-shot-" + (large ? "large" : "base") + (request.multiLabel() ? "-multi" : "-single"),
				() -> (large ? DebertaZeroShotClassifier.large() : DebertaZeroShotClassifier.base())
						.multiLabel(request.multiLabel())
						.build());
		long start = System.nanoTime();
		List<TextClassification> results = classifier.classify(request.text(), labels);
		long millis = (System.nanoTime() - start) / 1_000_000;
		return new ZeroShotResponse(results, millis, large ? "DeBERTa-v3 large" : "DeBERTa-v3 base",
				"This text is about {}.");
	}

}
