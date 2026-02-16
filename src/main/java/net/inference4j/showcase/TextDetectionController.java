package net.inference4j.showcase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.TextDetector;

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

	private final TextDetector detector;

	public TextDetectionController(TextDetector detector) {
		this.detector = detector;
	}

	@PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> detect(@RequestParam("image") MultipartFile file) throws IOException {
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}

		var regions = detector.detect(image, 0.4f, 0.4f);

		var annotated = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = annotated.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.setColor(new Color(79, 70, 229)); // accent color
		g.setStroke(new BasicStroke(2));
		for (var region : regions) {
			var box = region.box();
			int x = Math.round(box.x1());
			int y = Math.round(box.y1());
			int w = Math.round(box.width());
			int h = Math.round(box.height());
			g.drawRect(x, y, w, h);
		}
		g.dispose();

		var out = new ByteArrayOutputStream();
		ImageIO.write(annotated, "png", out);
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.body(out.toByteArray());
	}

}
