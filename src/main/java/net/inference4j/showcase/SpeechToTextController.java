package net.inference4j.showcase;

import java.io.IOException;
import java.nio.file.Files;

import io.github.inference4j.audio.Transcription;
import io.github.inference4j.audio.Wav2Vec2Recognizer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/speech-to-text")
public class SpeechToTextController {

	private final ModelCache cache;

	public SpeechToTextController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping
	public Transcription transcribe(@RequestParam("audio") MultipartFile file) throws IOException {
		var tmp = Files.createTempFile("speech-", ".wav");
		try {
			file.transferTo(tmp);
			var recognizer = cache.get("wav2vec2",
					() -> Wav2Vec2Recognizer.builder().build());
			return recognizer.transcribe(tmp);
		}
		finally {
			Files.deleteIfExists(tmp);
		}
	}

}
