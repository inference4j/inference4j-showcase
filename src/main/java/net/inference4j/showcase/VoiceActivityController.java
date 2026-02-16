package net.inference4j.showcase;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import io.github.inference4j.audio.VoiceActivityDetector;
import io.github.inference4j.audio.VoiceSegment;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice-activity")
public class VoiceActivityController {

	private final VoiceActivityDetector detector;

	public VoiceActivityController(VoiceActivityDetector detector) {
		this.detector = detector;
	}

	@PostMapping
	public VadResult detect(@RequestParam("audio") MultipartFile file) throws IOException {
		var tmp = Files.createTempFile("vad-", ".wav");
		try {
			file.transferTo(tmp);
			float duration = audioDuration(tmp);
			var segments = detector.detect(tmp);
			return new VadResult(duration, segments);
		}
		finally {
			Files.deleteIfExists(tmp);
		}
	}

	private float audioDuration(java.nio.file.Path path) throws IOException {
		try (var stream = AudioSystem.getAudioInputStream(path.toFile())) {
			var format = stream.getFormat();
			long frames = stream.getFrameLength();
			return frames / format.getFrameRate();
		}
		catch (UnsupportedAudioFileException e) {
			throw new IllegalArgumentException("Unsupported audio format", e);
		}
	}

	record VadResult(float totalDuration, List<VoiceSegment> segments) {
	}

}
