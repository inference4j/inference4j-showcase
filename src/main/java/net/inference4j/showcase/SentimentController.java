package net.inference4j.showcase;

import java.util.List;

import io.github.inference4j.nlp.TextClassification;
import io.github.inference4j.nlp.TextClassifier;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {

	private final TextClassifier classifier;

	public SentimentController(TextClassifier classifier) {
		this.classifier = classifier;
	}

	@PostMapping
	public List<TextClassification> analyze(@RequestBody String text) {
		var results = classifier.classify(text);
		return results;
	}

}
