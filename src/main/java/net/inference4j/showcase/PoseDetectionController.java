package net.inference4j.showcase;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import io.github.inference4j.vision.CocoJoint;
import io.github.inference4j.vision.ImageAnnotator;
import io.github.inference4j.vision.Pose;
import io.github.inference4j.vision.PoseDetector;
import io.github.inference4j.vision.RtmoPoseDetector;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pose detection with RTMO: draws each person's skeleton and reports the main joint angles, the numbers a
 * posture or exercise check is built on.
 */
@RestController
@RequestMapping("/api/pose-detection")
public class PoseDetectionController {

	/** Keypoints below this score are not drawn, and angles that need them are not reported. */
	private static final float MIN_KEYPOINT_CONFIDENCE = 0.3f;

	private static final Color[] PALETTE = {
		new Color(79, 70, 229),   // indigo
		new Color(16, 185, 129),  // emerald
		new Color(239, 68, 68),   // red
		new Color(245, 158, 11),  // amber
		new Color(6, 182, 212),   // cyan
		new Color(168, 85, 247),  // purple
		new Color(236, 72, 153),  // pink
		new Color(34, 197, 94),   // green
	};

	/** The angles shown per person: at the middle joint, between the lines to the outer two. */
	private static final List<AngleSpec> ANGLES = List.of(
			new AngleSpec("Left elbow", CocoJoint.LEFT_SHOULDER, CocoJoint.LEFT_ELBOW, CocoJoint.LEFT_WRIST),
			new AngleSpec("Right elbow", CocoJoint.RIGHT_SHOULDER, CocoJoint.RIGHT_ELBOW, CocoJoint.RIGHT_WRIST),
			new AngleSpec("Left shoulder", CocoJoint.LEFT_ELBOW, CocoJoint.LEFT_SHOULDER, CocoJoint.LEFT_HIP),
			new AngleSpec("Right shoulder", CocoJoint.RIGHT_ELBOW, CocoJoint.RIGHT_SHOULDER, CocoJoint.RIGHT_HIP),
			new AngleSpec("Left hip", CocoJoint.LEFT_SHOULDER, CocoJoint.LEFT_HIP, CocoJoint.LEFT_KNEE),
			new AngleSpec("Right hip", CocoJoint.RIGHT_SHOULDER, CocoJoint.RIGHT_HIP, CocoJoint.RIGHT_KNEE),
			new AngleSpec("Left knee", CocoJoint.LEFT_HIP, CocoJoint.LEFT_KNEE, CocoJoint.LEFT_ANKLE),
			new AngleSpec("Right knee", CocoJoint.RIGHT_HIP, CocoJoint.RIGHT_KNEE, CocoJoint.RIGHT_ANKLE));

	record AngleSpec(String name, CocoJoint a, CocoJoint b, CocoJoint c) {
	}

	/** A model the page can pick. */
	record ModelOption(String id, String label, String size, Supplier<PoseDetector> factory) {
	}

	private static final Map<String, ModelOption> MODELS = new LinkedHashMap<>();

	static {
		register(new ModelOption("rtmo-s", "RTMO-s", "38 MB", () -> RtmoPoseDetector.s().build()));
		register(new ModelOption("rtmo-m", "RTMO-m", "85 MB", () -> RtmoPoseDetector.m().build()));
		register(new ModelOption("rtmo-l", "RTMO-l", "168 MB", () -> RtmoPoseDetector.l().build()));
	}

	private static void register(ModelOption option) {
		MODELS.put(option.id(), option);
	}

	record ModelInfo(String id, String label, String size) {
	}

	private final ModelCache cache;

	public PoseDetectionController(ModelCache cache) {
		this.cache = cache;
	}

	@GetMapping("/models")
	public List<ModelInfo> models() {
		return MODELS.values().stream().map(m -> new ModelInfo(m.id(), m.label(), m.size())).toList();
	}

	@PostMapping
	public PoseResult detect(@RequestParam("image") MultipartFile file,
			@RequestParam(value = "model", defaultValue = "rtmo-s") String model,
			@RequestParam(value = "threshold", defaultValue = "0.5") float threshold) throws IOException {
		var image = ImageIO.read(file.getInputStream());
		if (image == null) {
			throw new IllegalArgumentException("Unsupported image format");
		}
		if (threshold < 0f || threshold > 1f) {
			throw new IllegalArgumentException("threshold must be between 0 and 1");
		}

		ModelOption option = MODELS.getOrDefault(model, MODELS.get("rtmo-s"));
		PoseDetector detector = cache.get("pose-" + option.id(), option.factory());

		long start = System.nanoTime();
		List<Pose> poses = detector.detect(image, threshold);
		long millis = (System.nanoTime() - start) / 1_000_000;

		// One colour per person; the annotator draws on a copy, so each call adds one skeleton
		float strokeWidth = Math.max(2f, Math.min(image.getWidth(), image.getHeight()) / 200f);
		BufferedImage annotated = image;
		List<PersonEntry> people = new ArrayList<>();
		for (int i = 0; i < poses.size(); i++) {
			Pose pose = poses.get(i);
			Color color = PALETTE[i % PALETTE.length];
			annotated = ImageAnnotator.annotatePoses(annotated, List.of(pose), CocoJoint.edges(),
					MIN_KEYPOINT_CONFIDENCE, color, strokeWidth);
			people.add(new PersonEntry(i + 1, pose.person().confidence(), hex(color), visibleKeypoints(pose),
					angles(pose)));
		}

		var out = new ByteArrayOutputStream();
		ImageIO.write(annotated, "png", out);
		return new PoseResult(Base64.getEncoder().encodeToString(out.toByteArray()), people, millis,
				option.label());
	}

	private static int visibleKeypoints(Pose pose) {
		return (int) pose.keypoints().stream().filter(k -> k.confidence() >= MIN_KEYPOINT_CONFIDENCE).count();
	}

	/** The angles whose three keypoints are all confidently placed; a hidden joint gives a meaningless angle. */
	private static List<AngleEntry> angles(Pose pose) {
		List<AngleEntry> angles = new ArrayList<>();
		for (AngleSpec spec : ANGLES) {
			boolean visible = confident(pose, spec.a()) && confident(pose, spec.b()) && confident(pose, spec.c());
			float degrees = visible ? pose.angle(spec.a(), spec.b(), spec.c()) : Float.NaN;
			angles.add(new AngleEntry(spec.name(), Float.isNaN(degrees) ? null : Math.round(degrees)));
		}
		return angles;
	}

	private static boolean confident(Pose pose, CocoJoint joint) {
		return pose.has(joint) && pose.keypoint(joint).confidence() >= MIN_KEYPOINT_CONFIDENCE;
	}

	private static String hex(Color c) {
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	record PoseResult(String image, List<PersonEntry> people, long millis, String model) {
	}

	record PersonEntry(int number, float confidence, String color, int visibleKeypoints, List<AngleEntry> angles) {
	}

	/** An angle in whole degrees, or {@code null} when a joint it needs is not confidently visible. */
	record AngleEntry(String name, Integer degrees) {
	}

}
