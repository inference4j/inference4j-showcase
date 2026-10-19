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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.BoundingBox;
import io.github.inference4j.vision.Detection;
import io.github.inference4j.vision.ImageFeatures;
import io.github.inference4j.vision.ObjectDetector;
import io.github.inference4j.vision.Prompt;
import io.github.inference4j.vision.RtDetrDetector;
import io.github.inference4j.vision.Sam2Segmenter;
import io.github.inference4j.vision.SegmentationMask;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Two SAM 2.1 demos: click to segment (encode once, then one cheap decoder run per click), and detect + segment (a
 * detector's boxes become box prompts, one mask per object).
 */
@RestController
@RequestMapping("/api/segmentation")
public class SegmentationController {

	/** Larger uploads are scaled down first: masks and PNGs at full camera resolution make every click slow. */
	private static final int MAX_SIDE = 1600;

	/** Encoded images kept for clicking. Each holds about 17 MB of features, so only the most recent few stay. */
	private static final int MAX_SESSIONS = 6;

	/** Detect + segment caps the objects it segments, so a crowded photo stays responsive. */
	private static final int MAX_OBJECTS = 20;

	private static final float MIN_CONFIDENCE = 0.5f;

	private static final Color MASK_COLOR = new Color(79, 70, 229);

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

	private record Session(BufferedImage image, ImageFeatures features) {
	}

	private final Map<String, Session> sessions = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
			return size() > MAX_SESSIONS;
		}
	};

	private final ModelCache cache;

	public SegmentationController(ModelCache cache) {
		this.cache = cache;
	}

	// --- Click to segment ---

	/** Encodes an image once; every click on it then only runs the decoder. */
	@PostMapping("/encode")
	public EncodeResult encode(@RequestParam("image") MultipartFile file) throws IOException {
		BufferedImage image = fitWithin(readImage(file));

		long start = System.nanoTime();
		ImageFeatures features = segmenter().encode(image);
		long millis = (System.nanoTime() - start) / 1_000_000;

		String id = UUID.randomUUID().toString();
		synchronized (sessions) {
			sessions.put(id, new Session(image, features));
		}
		return new EncodeResult(id, image.getWidth(), image.getHeight(), millis);
	}

	@PostMapping("/segment")
	public SegmentResult segment(@RequestBody SegmentRequest request) {
		Session session;
		synchronized (sessions) {
			session = sessions.get(request.id());
		}
		if (session == null) {
			throw new ResponseStatusException(HttpStatus.GONE, "This image expired, please upload it again");
		}
		Prompt prompt = toPrompt(request);

		long start = System.nanoTime();
		List<SegmentationMask> masks = segmenter().segmentCandidates(session.features(), prompt);
		long millis = (System.nanoTime() - start) / 1_000_000;

		List<Candidate> candidates = masks.stream()
				.map(m -> new Candidate(png(maskImage(m)), m.score(), m.area()))
				.toList();
		return new SegmentResult(candidates, millis);
	}

	private static Prompt toPrompt(SegmentRequest request) {
		Prompt prompt = null;
		if (request.box() != null) {
			Box b = request.box();
			prompt = Prompt.box(Math.min(b.x1(), b.x2()), Math.min(b.y1(), b.y2()),
					Math.max(b.x1(), b.x2()), Math.max(b.y1(), b.y2()));
		}
		for (Click click : request.clicks() == null ? List.<Click>of() : request.clicks()) {
			if (prompt == null && !click.foreground()) {
				// An exclusion only means something next to a click or box that selects the object
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Click the object first, then exclude parts of it");
			}
			if (prompt == null) {
				prompt = Prompt.point(click.x(), click.y());
			}
			else {
				prompt = click.foreground() ? prompt.withPoint(click.x(), click.y())
						: prompt.withBackground(click.x(), click.y());
			}
		}
		if (prompt == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Click the image or draw a box first");
		}
		return prompt;
	}

	/** The mask as a PNG: opaque colour on the object, transparent elsewhere, for the page to draw over the photo. */
	private static BufferedImage maskImage(SegmentationMask mask) {
		BufferedImage image = new BufferedImage(mask.width(), mask.height(), BufferedImage.TYPE_INT_ARGB);
		int rgb = 0xFF000000 | (MASK_COLOR.getRGB() & 0xFFFFFF);
		for (int y = 0; y < mask.height(); y++) {
			for (int x = 0; x < mask.width(); x++) {
				if (mask.contains(x, y)) {
					image.setRGB(x, y, rgb);
				}
			}
		}
		return image;
	}

	// --- Detect + segment ---

	@PostMapping("/detect")
	public DetectResult detect(@RequestParam("image") MultipartFile file) throws IOException {
		BufferedImage image = fitWithin(readImage(file));

		long start = System.nanoTime();
		List<Detection> detections = detector().detect(image).stream()
				.filter(d -> d.confidence() >= MIN_CONFIDENCE)
				.limit(MAX_OBJECTS)
				.toList();
		long detectMillis = (System.nanoTime() - start) / 1_000_000;

		start = System.nanoTime();
		Sam2Segmenter sam = segmenter();
		ImageFeatures features = sam.encode(image);
		long encodeMillis = (System.nanoTime() - start) / 1_000_000;

		start = System.nanoTime();
		BufferedImage overlay = image;
		List<SegmentedObject> objects = new ArrayList<>();
		List<SegmentationMask> masks = new ArrayList<>();
		for (int i = 0; i < detections.size(); i++) {
			Detection d = detections.get(i);
			SegmentationMask mask = sam.segment(features, Prompt.box(d.box()));
			masks.add(mask);
			Color color = PALETTE[i % PALETTE.length];
			overlay = mask.overlay(overlay, color);
			objects.add(new SegmentedObject(d.label(), d.confidence(), mask.score(), mask.area(), hex(color),
					mask.boundingBox().map(box -> png(crop(mask.applyTo(image), box))).orElse(null)));
		}
		long segmentMillis = (System.nanoTime() - start) / 1_000_000;

		return new DetectResult(png(labelled(overlay, detections, masks)), objects, detectMillis, encodeMillis,
				segmentMillis);
	}

	/** Writes each object's label at the top-left of its mask. */
	private static BufferedImage labelled(BufferedImage overlay, List<Detection> detections,
			List<SegmentationMask> masks) {
		BufferedImage result = new BufferedImage(overlay.getWidth(), overlay.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(overlay, 0, 0, null);
		int fontSize = Math.max(12, Math.min(overlay.getWidth(), overlay.getHeight()) / 40);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
		g.setStroke(new BasicStroke(1));
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < detections.size(); i++) {
			BoundingBox box = masks.get(i).boundingBox().orElse(detections.get(i).box());
			String text = detections.get(i).label();
			int x = Math.round(box.x1());
			int y = Math.max(Math.round(box.y1()) - fm.getHeight() - 2, 0);
			g.setColor(PALETTE[i % PALETTE.length]);
			g.fillRect(x, y, fm.stringWidth(text) + 8, fm.getHeight() + 2);
			g.setColor(Color.WHITE);
			g.drawString(text, x + 4, y + fm.getAscent());
		}
		g.dispose();
		return result;
	}

	private static BufferedImage crop(BufferedImage image, BoundingBox box) {
		int x = Math.round(box.x1());
		int y = Math.round(box.y1());
		int w = Math.min(Math.round(box.width()), image.getWidth() - x);
		int h = Math.min(Math.round(box.height()), image.getHeight() - y);
		return image.getSubimage(x, y, w, h);
	}

	// --- Models and helpers ---

	private Sam2Segmenter segmenter() {
		return cache.get("sam2.1-hiera-tiny", () -> Sam2Segmenter.builder().build());
	}

	private ObjectDetector detector() {
		// Same key as the object detection demo on the CPU, so the two pages share one loaded detector
		return cache.get("detector-dfine-small-cpu", () -> RtDetrDetector.dfineSmall().build());
	}

	private static BufferedImage readImage(MultipartFile file) throws IOException {
		BufferedImage image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}
		return image;
	}

	private static BufferedImage fitWithin(BufferedImage image) {
		int longest = Math.max(image.getWidth(), image.getHeight());
		BufferedImage rgb;
		int w = image.getWidth();
		int h = image.getHeight();
		if (longest > MAX_SIDE) {
			float scale = (float) MAX_SIDE / longest;
			w = Math.round(image.getWidth() * scale);
			h = Math.round(image.getHeight() * scale);
		}
		rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, w, h, null);
		g.dispose();
		return rgb;
	}

	private static String png(BufferedImage image) {
		try {
			var out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return Base64.getEncoder().encodeToString(out.toByteArray());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String hex(Color c) {
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	record EncodeResult(String id, int width, int height, long millis) {
	}

	record Click(float x, float y, boolean foreground) {
	}

	record Box(float x1, float y1, float x2, float y2) {
	}

	record SegmentRequest(String id, List<Click> clicks, Box box) {
	}

	record Candidate(String mask, float score, int area) {
	}

	record SegmentResult(List<Candidate> candidates, long millis) {
	}

	record SegmentedObject(String label, float confidence, float maskScore, int area, String color, String cutout) {
	}

	record DetectResult(String image, List<SegmentedObject> objects, long detectMillis, long encodeMillis,
			long segmentMillis) {
	}

}
