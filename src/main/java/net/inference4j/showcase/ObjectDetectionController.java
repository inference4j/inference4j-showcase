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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.imageio.ImageIO;

import io.github.inference4j.session.SessionConfigurer;
import io.github.inference4j.vision.Detection;
import io.github.inference4j.vision.ObjectDetector;
import io.github.inference4j.vision.RtDetrDetector;
import io.github.inference4j.vision.Yolo26Detector;

import org.springframework.web.bind.annotation.GetMapping;
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

	/** A detector the page can pick: how to build it, and what to tell the user about it. */
	record ModelOption(String id, String label, String license, String size, Accelerators.CoreMl coreMl,
			Function<SessionConfigurer, ObjectDetector> factory) {
	}

	private static final Map<String, ModelOption> MODELS = new LinkedHashMap<>();

	static {
		// MLProgram: CoreML's default NeuralNetwork format changes YOLO26's detections and some RT-DETR labels
		register(new ModelOption("dfine-small", "D-FINE small", "Apache-2.0", "41 MB", Accelerators.CoreMl.ML_PROGRAM,
				opts -> RtDetrDetector.dfineSmall().sessionOptions(opts).build()));
		// This export's dynamic query count makes CoreML fail (inference4j/onnx-exporter#7)
		register(new ModelOption("dfine-nano", "D-FINE nano", "Apache-2.0", "15 MB", Accelerators.CoreMl.UNSUPPORTED,
				opts -> RtDetrDetector.dfineNano().sessionOptions(opts).build()));
		register(new ModelOption("rtdetr-v2", "RT-DETRv2 R18", "Apache-2.0", "81 MB", Accelerators.CoreMl.ML_PROGRAM,
				opts -> RtDetrDetector.rtDetrV2().sessionOptions(opts).build()));
		register(new ModelOption("yolo26n", "YOLO26n", "AGPL-3.0", "10 MB", Accelerators.CoreMl.ML_PROGRAM,
				opts -> Yolo26Detector.builder().sessionOptions(opts).build()));
	}

	private static void register(ModelOption option) {
		MODELS.put(option.id(), option);
	}

	record ModelInfo(String id, String label, String license, String size, boolean coreMlSupported) {
	}

	private final ModelCache cache;
	private final Accelerators accelerators;

	public ObjectDetectionController(ModelCache cache, Accelerators accelerators) {
		this.cache = cache;
		this.accelerators = accelerators;
	}

	@GetMapping("/models")
	public List<ModelInfo> models() {
		return MODELS.values().stream()
				.map(m -> new ModelInfo(m.id(), m.label(), m.license(), m.size(), m.coreMl() != Accelerators.CoreMl.UNSUPPORTED))
				.toList();
	}

	@PostMapping
	public DetectionResult detect(@RequestParam("image") MultipartFile file,
			@RequestParam(value = "model", defaultValue = "dfine-small") String model,
			@RequestParam(value = "device", defaultValue = "cpu") String device) throws IOException {
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}

		ModelOption option = MODELS.getOrDefault(model, MODELS.get("dfine-small"));
		String resolved = accelerators.resolve(device, option.coreMl());
		ObjectDetector detector = cache.get("detector-" + option.id() + "-" + resolved,
				() -> option.factory().apply(accelerators.sessionOptions(resolved, option.coreMl())));
		long start = System.nanoTime();
		var detections = detector.detect(image);
		long millis = (System.nanoTime() - start) / 1_000_000;

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

		return new DetectionResult(imageBase64, entries, millis, accelerators.label(resolved), option.label(),
				option.license());
	}

	record DetectionResult(String image, List<DetectionEntry> detections, long millis, String device, String model,
			String license) {
	}

	record DetectionEntry(String label, float confidence, String color) {
	}

}
