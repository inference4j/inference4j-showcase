package net.inference4j.showcase;

import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.nlp.CoeditGrammarCorrector;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grammar")
public class GrammarController {

	private final ModelCache cache;

	public GrammarController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping
	public CorrectionResult correct(@RequestBody String text) {
		var corrector = cache.get("coedit-base",
				() -> CoeditGrammarCorrector.coeditBase().build());
		GenerationResult result = corrector.correct(text, token -> {});
		return new CorrectionResult(result.text(), result.generatedTokens(),
				result.duration().toMillis());
	}

	record CorrectionResult(String corrected, int tokens, long durationMs) {}

}
