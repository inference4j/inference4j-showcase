package net.inference4j.showcase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.Detection;
import io.github.inference4j.vision.ObjectDetector;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/object-detection")
public class ObjectDetectionController {

	private static final Color[] PALETTE = {
		new Color(79, 70, 229),   // indigo
		new Color(16, 185, 129),  // emerald
		new Color(239, 68, 68),   // red
		new Color(245, 158, 11),  // amber
		new Color(6, 182, 212),   // cyan
		new Color(168, 85, 247),  // purple
		new Color(236, 72, 153),  // pink
		new Color(34, 197, 94),   // green
		new Color(59, 130, 246),  // blue
		new Color(251, 146, 60),  // orange
	};

	private final ObjectDetector detector;

	public ObjectDetectionController(ObjectDetector detector) {
		this.detector = detector;
	}

	@PostMapping
	public DetectionResult detect(@RequestParam("image") MultipartFile file) throws IOException {
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}

		var detections = detector.detect(image);

		var annotated = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = annotated.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(image, 0, 0, null);

		float strokeWidth = Math.max(2, Math.min(image.getWidth(), image.getHeight()) / 300f);
		g.setStroke(new BasicStroke(strokeWidth));
		int fontSize = Math.max(12, Math.min(image.getWidth(), image.getHeight()) / 40);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
		FontMetrics fm = g.getFontMetrics();

		for (Detection det : detections) {
			Color color = PALETTE[det.classIndex() % PALETTE.length];
			var box = det.box();
			int x = Math.round(box.x1());
			int y = Math.round(box.y1());
			int w = Math.round(box.width());
			int h = Math.round(box.height());

			g.setColor(color);
			g.drawRect(x, y, w, h);

			String text = String.format("%s %.0f%%", det.label(), det.confidence() * 100);
			int textWidth = fm.stringWidth(text);
			int textHeight = fm.getHeight();
			int labelY = Math.max(y - textHeight - 2, 0);
			g.setColor(color);
			g.fillRect(x, labelY, textWidth + 8, textHeight + 2);

			g.setColor(Color.WHITE);
			g.drawString(text, x + 4, labelY + fm.getAscent());
		}

		g.dispose();

		var out = new ByteArrayOutputStream();
		ImageIO.write(annotated, "png", out);
		String imageBase64 = Base64.getEncoder().encodeToString(out.toByteArray());

		List<DetectionEntry> entries = detections.stream()
				.map(det -> {
					Color c = PALETTE[det.classIndex() % PALETTE.length];
					String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
					return new DetectionEntry(det.label(), det.confidence(), hex);
				})
				.toList();

		return new DetectionResult(imageBase64, entries);
	}

	record DetectionResult(String image, List<DetectionEntry> detections) {
	}

	record DetectionEntry(String label, float confidence, String color) {
	}

}
