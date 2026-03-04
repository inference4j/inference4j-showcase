package net.inference4j.showcase;

import java.util.List;

import io.github.inference4j.nlp.BertNerRecognizer;
import io.github.inference4j.nlp.NamedEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ner")
public class NerController {

	private final ModelCache cache;

	public NerController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping
	public List<NamedEntity> recognize(@RequestBody String text) {
		var recognizer = cache.get("distilbert-ner",
				() -> BertNerRecognizer.builder().build());
		return recognizer.recognize(text);
	}

}
