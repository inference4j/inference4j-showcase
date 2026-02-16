package net.inference4j.showcase;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.Classification;
import io.github.inference4j.vision.ImageClassifier;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/image-classification")
public class ImageClassificationController {

	private final Map<String, ImageClassifier> classifiers;

	public ImageClassificationController(Map<String, ImageClassifier> imageClassifiers) {
		this.classifiers = imageClassifiers;
	}

	@PostMapping
	public List<Classification> classify(
			@RequestParam("image") MultipartFile file,
			@RequestParam(value = "model", defaultValue = "resnet") String model) throws IOException {

		var classifier = classifiers.get(model);
		if (classifier == null) {
			throw new IllegalArgumentException("Unknown model: " + model);
		}
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}
		return classifier.classify(image, 5);
	}

}
