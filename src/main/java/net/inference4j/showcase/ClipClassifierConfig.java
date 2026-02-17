package net.inference4j.showcase;

import io.github.inference4j.multimodal.ClipClassifier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClipClassifierConfig {

	@Bean
	ClipClassifier clipClassifier() {
		return ClipClassifier.builder().build();
	}

}
