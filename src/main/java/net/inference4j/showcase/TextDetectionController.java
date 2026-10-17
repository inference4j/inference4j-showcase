package net.inference4j.showcase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.CraftTextDetector;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/text-detection")
public class TextDetectionController {

	private final ModelCache cache;
	private final Accelerators accelerators;

	public TextDetectionController(ModelCache cache, Accelerators accelerators) {
		this.cache = cache;
		this.accelerators = accelerators;
	}

	@PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> detect(@RequestParam("image") MultipartFile file,
			@RequestParam(value = "device", defaultValue = "cpu") String device) throws IOException {
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}

		String resolved = accelerators.resolve(device);
		var detector = cache.get("craft-" + resolved, () -> CraftTextDetector.builder()
				.sessionOptions(accelerators.sessionOptions(resolved))
				.build());
		long start = System.nanoTime();
		var regions = detector.detect(image);
		long millis = (System.nanoTime() - start) / 1_000_000;

		var annotated = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = annotated.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(image, 0, 0, null);
		var accent = new Color(79, 70, 229);
		for (var region : regions) {
			// The outline follows rotated text; the axis-aligned box would not
			var outline = new Path2D.Float();
			var corners = region.polygon();
			outline.moveTo(corners.get(0).x(), corners.get(0).y());
			for (int i = 1; i < corners.size(); i++) {
				outline.lineTo(corners.get(i).x(), corners.get(i).y());
			}
			outline.closePath();
			// A white halo keeps the outline visible on dark and colored backgrounds
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(4));
			g.draw(outline);
			g.setColor(accent);
			g.setStroke(new BasicStroke(2));
			g.draw(outline);
		}
		g.dispose();

		var out = new ByteArrayOutputStream();
		ImageIO.write(annotated, "png", out);
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.header("X-Inference-Millis", String.valueOf(millis))
				.header("X-Inference-Device", accelerators.label(resolved))
				.header("X-Region-Count", String.valueOf(regions.size()))
				.body(out.toByteArray());
	}

}
