package net.inference4j.showcase;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import io.github.inference4j.multimodal.ClipClassifier;
import io.github.inference4j.vision.Classification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/visual-search")
public class VisualSearchController {

	private final ClipClassifier classifier;

	public VisualSearchController(ClipClassifier clipClassifier) {
		this.classifier = clipClassifier;
	}

	@PostMapping
	public List<Classification> classify(
			@RequestParam("image") MultipartFile file,
			@RequestParam("labels") String labels) throws IOException {

		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}
		List<String> candidateLabels = Arrays.stream(labels.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
		if (candidateLabels.isEmpty()) {
			throw new IllegalArgumentException("At least one label is required");
		}
		return classifier.classify(image, candidateLabels);
	}

}
