package net.inference4j.showcase;

import java.util.List;

import io.github.inference4j.nlp.DistilBertTextClassifier;
import io.github.inference4j.nlp.TextClassification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {

	private final ModelCache cache;

	public SentimentController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping
	public List<TextClassification> analyze(@RequestBody String text) {
		var classifier = cache.get("distilbert-sentiment",
				() -> DistilBertTextClassifier.builder().build());
		return classifier.classify(text);
	}

}
