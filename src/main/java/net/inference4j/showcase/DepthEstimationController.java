package net.inference4j.showcase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.Colormap;
import io.github.inference4j.vision.DepthAnythingEstimator;
import io.github.inference4j.vision.DepthMap;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/depth-estimation")
public class DepthEstimationController {

	private final ModelCache cache;

	public DepthEstimationController(ModelCache cache) {
		this.cache = cache;
	}

	@PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> estimate(@RequestParam("image") MultipartFile file,
			@RequestParam(value = "colormap", defaultValue = "TURBO") String colormapName) throws IOException {

		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}

		Colormap colormap = parseColormap(colormapName);

		var estimator = cache.get("depth-anything", () -> DepthAnythingEstimator.builder().build());
		DepthMap depth = estimator.estimate(image);

		// Depth values are relative inverse depth on a per-image scale, so the
		// colormap rescales to this map's own range rather than a fixed one.
		var rendered = depth.toImage(colormap);

		var out = new ByteArrayOutputStream();
		ImageIO.write(rendered, "png", out);
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.header("X-Depth-Min", Float.toString(depth.min()))
				.header("X-Depth-Max", Float.toString(depth.max()))
				.body(out.toByteArray());
	}

	private static Colormap parseColormap(String name) {
		try {
			return Colormap.valueOf(name.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown colormap: " + name
					+ ". Expected one of GRAYSCALE, VIRIDIS, TURBO.");
		}
	}

}
