package net.inference4j.showcase;

import java.awt.image.BufferedImage;
import java.util.Map;

import io.github.inference4j.nlp.TextClassifier;
import io.github.inference4j.vision.ImageClassifier;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ModelWarmup {

	private final TextClassifier textClassifier;

	private final Map<String, ImageClassifier> imageClassifiers;

	public ModelWarmup(TextClassifier textClassifier, Map<String, ImageClassifier> imageClassifiers) {
		this.textClassifier = textClassifier;
		this.imageClassifiers = imageClassifiers;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmup() {
		textClassifier.classify("warmup");
		var dummy = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
		imageClassifiers.values().forEach(c -> c.classify(dummy));
	}

}
