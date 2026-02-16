package net.inference4j.showcase;

import java.awt.image.BufferedImage;
import java.util.Map;

import io.github.inference4j.audio.SpeechRecognizer;
import io.github.inference4j.audio.VoiceActivityDetector;
import io.github.inference4j.nlp.TextClassifier;
import io.github.inference4j.vision.ImageClassifier;
import io.github.inference4j.vision.TextDetector;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ModelWarmup {

	private final TextClassifier textClassifier;

	private final Map<String, ImageClassifier> imageClassifiers;

	private final TextDetector textDetector;

	private final SpeechRecognizer speechRecognizer;

	private final VoiceActivityDetector voiceActivityDetector;

	public ModelWarmup(TextClassifier textClassifier, Map<String, ImageClassifier> imageClassifiers,
			TextDetector textDetector, SpeechRecognizer speechRecognizer,
			VoiceActivityDetector voiceActivityDetector) {
		this.textClassifier = textClassifier;
		this.imageClassifiers = imageClassifiers;
		this.textDetector = textDetector;
		this.speechRecognizer = speechRecognizer;
		this.voiceActivityDetector = voiceActivityDetector;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmup() {
		textClassifier.classify("warmup");
		var dummy = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
		imageClassifiers.values().forEach(c -> c.classify(dummy));
		textDetector.detect(dummy);
		var silence = new float[16000];
		speechRecognizer.transcribe(silence, 16000);
		voiceActivityDetector.detect(silence, 16000);
	}

}
