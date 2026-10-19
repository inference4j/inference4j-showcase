package net.inference4j.showcase;

import java.util.List;
import java.util.Locale;

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
@RequestMapping("/api/pii")
public class PiiController {

	private final ModelCache cache;

	public PiiController(ModelCache cache) {
		this.cache = cache;
	}

	record PiiRequest(String text, List<String> labels) {
	}

	record PiiResponse(List<NamedEntity> entities, String redacted, long millis) {
	}

	@PostMapping
	public PiiResponse detect(@RequestBody PiiRequest request) {
		List<String> labels = Labels.clean(request.labels());
		if (request.text() == null || request.text().isBlank() || labels.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a text and pick at least one type");
		}
		// 0.3 is the threshold the PII model's authors recommend
		ZeroShotEntityRecognizer recognizer = cache.get("gliner-pii",
				() -> GlinerEntityRecognizer.pii().threshold(0.3f).build());
		long start = System.nanoTime();
		List<NamedEntity> entities = recognizer.recognize(request.text(), labels);
		long millis = (System.nanoTime() - start) / 1_000_000;
		return new PiiResponse(entities, redact(request.text(), entities), millis);
	}

	/** Replaces each entity with its label, e.g. {@code [PHONE_NUMBER]}. Entities never overlap. */
	static String redact(String text, List<NamedEntity> entities) {
		StringBuilder redacted = new StringBuilder();
		int cursor = 0;
		for (NamedEntity entity : entities) {
			redacted.append(text, cursor, entity.start())
				.append('[')
				.append(entity.label().toUpperCase(Locale.ROOT).replaceAll("\\W+", "_"))
				.append(']');
			cursor = entity.end();
		}
		return redacted.append(text.substring(cursor)).toString();
	}

}
