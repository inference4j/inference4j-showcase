package net.inference4j.showcase;

import java.util.List;

import io.github.inference4j.nlp.BertNerRecognizer;
import io.github.inference4j.nlp.GlinerEntityRecognizer;
import io.github.inference4j.nlp.NamedEntity;
import io.github.inference4j.nlp.ZeroShotEntityRecognizer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ner")
public class NerController {

	private final ModelCache cache;

	public NerController(ModelCache cache) {
		this.cache = cache;
	}

	record NerRequest(String text, String model, List<String> labels) {
	}

	record NerResponse(List<NamedEntity> entities, long millis, String model) {
	}

	@PostMapping
	public NerResponse recognize(@RequestBody NerRequest request) {
		if (request.text() == null || request.text().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a text");
		}
		if ("gliner".equals(request.model())) {
			List<String> labels = Labels.clean(request.labels());
			if (labels.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter at least one label");
			}
			ZeroShotEntityRecognizer recognizer = cache.get("gliner-small",
					() -> GlinerEntityRecognizer.small().build());
			long start = System.nanoTime();
			List<NamedEntity> entities = recognizer.recognize(request.text(), labels);
			return new NerResponse(entities, (System.nanoTime() - start) / 1_000_000, "GLiNER small v2.1");
		}
		var recognizer = cache.get("distilbert-ner", () -> BertNerRecognizer.builder().build());
		long start = System.nanoTime();
		List<NamedEntity> entities = recognizer.recognize(request.text());
		return new NerResponse(entities, (System.nanoTime() - start) / 1_000_000, "DistilBERT NER");
	}

}
