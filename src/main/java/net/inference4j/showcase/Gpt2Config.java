package net.inference4j.showcase;

import io.github.inference4j.nlp.Gpt2TextGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class Gpt2Config {

	@Lazy
	@Bean
	Gpt2TextGenerator gpt2TextGenerator() {
		return Gpt2TextGenerator.builder()
			.maxNewTokens(256)
			.temperature(0.8f)
			.topK(50)
			.topP(0.9f)
			.build();
	}

}
