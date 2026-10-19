package net.inference4j.showcase;

import io.github.inference4j.nlp.DebertaPromptInjectionDetector;
import io.github.inference4j.nlp.InjectionCheck;
import io.github.inference4j.nlp.PromptInjectionDetector;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/prompt-injection")
public class PromptInjectionController {

	private final ModelCache cache;

	public PromptInjectionController(ModelCache cache) {
		this.cache = cache;
	}

	record InjectionResponse(boolean injection, float probability, float threshold, long millis) {
	}

	@PostMapping
	public InjectionResponse check(@RequestBody String text) {
		if (text == null || text.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a text");
		}
		PromptInjectionDetector detector = cache.get("prompt-injection",
				() -> DebertaPromptInjectionDetector.builder().build());
		long start = System.nanoTime();
		InjectionCheck check = detector.check(text);
		long millis = (System.nanoTime() - start) / 1_000_000;
		return new InjectionResponse(check.isInjection(), check.probability(), check.threshold(), millis);
	}

}
