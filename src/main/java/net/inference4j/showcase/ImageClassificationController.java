package net.inference4j.showcase;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.Classification;
import io.github.inference4j.vision.EfficientNetClassifier;
import io.github.inference4j.vision.ImageClassifier;
import io.github.inference4j.vision.ResNetClassifier;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/image-classification")
public class ImageClassificationController {

	private static final Map<String, java.util.function.Supplier<ImageClassifier>> FACTORIES = Map.of(
		"resnet", () -> ResNetClassifier.builder().build(),
		"efficientnet", () -> EfficientNetClassifier.builder().build()
	);

	private final ModelCache cache;

	public ImageClassificationController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping
	public List<Classification> classify(
			@RequestParam("image") MultipartFile file,
			@RequestParam(value = "model", defaultValue = "resnet") String model) throws IOException {

		var factory = FACTORIES.get(model);
		if (factory == null) {
			throw new IllegalArgumentException("Unknown model: " + model);
		}
		var classifier = cache.get(model, factory);
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}
		return classifier.classify(image, 5);
	}

}
