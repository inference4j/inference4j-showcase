package net.inference4j.showcase;

import io.github.inference4j.genai.ModelSources;
import io.github.inference4j.genai.nlp.TextGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class TextGeneratorConfig {

	@Lazy
	@Bean
	TextGenerator phi3Generator() {
		return TextGenerator.builder()
			.model(ModelSources.phi3Mini())
			.maxLength(512)
			.temperature(0.7)
			.build();
	}

	@Lazy
	@Bean
	TextGenerator deepSeekGenerator() {
		return TextGenerator.builder()
			.model(ModelSources.deepSeekR1_1_5B())
			.maxLength(512)
			.temperature(0.7)
			.build();
	}

}
