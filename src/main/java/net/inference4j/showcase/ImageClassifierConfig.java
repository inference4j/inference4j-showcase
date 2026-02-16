package net.inference4j.showcase;

import java.util.Map;

import io.github.inference4j.vision.EfficientNetClassifier;
import io.github.inference4j.vision.ImageClassifier;
import io.github.inference4j.vision.ResNetClassifier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ImageClassifierConfig {

	@Bean
	Map<String, ImageClassifier> imageClassifiers() {
		return Map.of(
			"resnet", ResNetClassifier.builder().build(),
			"efficientnet", EfficientNetClassifier.builder().build()
		);
	}

}
