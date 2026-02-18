package net.inference4j.showcase;

import io.github.inference4j.genai.ModelSources;
import io.github.inference4j.vision.VisionLanguageModel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class VisionLanguageModelConfig {

	@Lazy
	@Bean
	VisionLanguageModel visionLanguageModel() {
		return VisionLanguageModel.builder()
			.model(ModelSources.phi3Vision())
			.maxLength(4096)
			.build();
	}

}
