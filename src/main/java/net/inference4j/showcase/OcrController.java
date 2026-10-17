package net.inference4j.showcase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.CraftTextDetector;
import io.github.inference4j.vision.OcrPipeline;
import io.github.inference4j.vision.PaddleTextDetector;
import io.github.inference4j.vision.PaddleTextRecognizer;
import io.github.inference4j.vision.TextDetector;
import io.github.inference4j.vision.TextLine;
import io.github.inference4j.vision.TextRecognizer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

	private static final Color ACCENT = new Color(79, 70, 229);

	private final ModelCache cache;
	private final Accelerators accelerators;

	public OcrController(ModelCache cache, Accelerators accelerators) {
		this.cache = cache;
		this.accelerators = accelerators;
	}

	record Line(int index, String text, float confidence, List<float[]> polygon) {
	}

	record OcrResponse(List<Line> lines, String annotatedImage, long millis, String device) {
	}

	@PostMapping
	public OcrResponse read(@RequestParam("image") MultipartFile file,
			@RequestParam(value = "models", defaultValue = "small") String models,
			@RequestParam(value = "detector", defaultValue = "paddle") String detector,
			@RequestParam(value = "device", defaultValue = "cpu") String device) throws IOException {
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}
		boolean tiny = "tiny".equals(models);
		boolean craft = "craft".equals(detector);

		String resolved = accelerators.resolve(device);
		var sessionOptions = accelerators.sessionOptions(resolved);
		String key = "ocr-" + (craft ? "craft" : "paddle") + "-" + (tiny ? "tiny" : "small") + "-" + resolved;
		OcrPipeline ocr = cache.get(key, () -> {
			TextDetector textDetector = craft ? CraftTextDetector.builder().sessionOptions(sessionOptions).build()
					: (tiny ? PaddleTextDetector.tiny() : PaddleTextDetector.small()).sessionOptions(sessionOptions).build();
			TextRecognizer recognizer = (tiny ? PaddleTextRecognizer.tiny() : PaddleTextRecognizer.small())
					.sessionOptions(sessionOptions).build();
			return OcrPipeline.builder().detector(textDetector).recognizer(recognizer).build();
		});

		long start = System.nanoTime();
		List<TextLine> textLines = ocr.read(image);
		long millis = (System.nanoTime() - start) / 1_000_000;

		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < textLines.size(); i++) {
			TextLine line = textLines.get(i);
			List<float[]> polygon = line.region().polygon().stream().map(p -> new float[] { p.x(), p.y() }).toList();
			lines.add(new Line(i + 1, line.text(), line.confidence(), polygon));
		}
		return new OcrResponse(lines, annotate(image, lines), millis, accelerators.label(resolved));
	}

	/** The image with each line's outline drawn and numbered, as a PNG data URL. */
	private static String annotate(BufferedImage image, List<Line> lines) throws IOException {
		var annotated = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = annotated.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.drawImage(image, 0, 0, null);
		int shortSide = Math.min(image.getWidth(), image.getHeight());
		float stroke = Math.max(2f, shortSide / 400f);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, Math.round(shortSide / 32f))));
		for (Line line : lines) {
			var outline = new Path2D.Float();
			outline.moveTo(line.polygon().get(0)[0], line.polygon().get(0)[1]);
			for (int i = 1; i < line.polygon().size(); i++) {
				outline.lineTo(line.polygon().get(i)[0], line.polygon().get(i)[1]);
			}
			outline.closePath();
			// A white halo keeps the outline visible on dark and colored backgrounds
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(stroke + 2));
			g.draw(outline);
			g.setColor(ACCENT);
			g.setStroke(new BasicStroke(stroke));
			g.draw(outline);

			// Number badge at the outline's top-left corner
			String label = String.valueOf(line.index());
			var metrics = g.getFontMetrics();
			int padding = Math.max(3, metrics.getAscent() / 4);
			int w = metrics.stringWidth(label) + 2 * padding;
			int h = metrics.getAscent() + padding;
			int x = Math.max(0, Math.round(line.polygon().get(0)[0]));
			int y = Math.max(0, Math.round(line.polygon().get(0)[1]) - h);
			g.fillRect(x, y, w, h);
			g.setColor(Color.WHITE);
			g.drawString(label, x + padding, y + metrics.getAscent());
		}
		g.dispose();

		var out = new ByteArrayOutputStream();
		ImageIO.write(annotated, "png", out);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
	}

}
