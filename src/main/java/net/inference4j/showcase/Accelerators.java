package net.inference4j.showcase;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;
import io.github.inference4j.session.SessionConfigurer;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ONNX Runtime execution providers this machine offers: CPU always, CoreML on macOS, CUDA with the
 * {@code onnxruntime_gpu} build.
 */
@Component
@RestController
public class Accelerators {

	record Device(String id, String label) {
	}

	/**
	 * How a model runs under CoreML. The default NeuralNetwork format is fastest for some models (CRAFT) but computes
	 * with reduced precision, which changes some models' results (YOLO26); MLProgram matches the CPU for the models
	 * it can convert; some exports convert to neither.
	 */
	public enum CoreMl {
		NEURAL_NETWORK, ML_PROGRAM, UNSUPPORTED
	}

	private final List<Device> devices;

	public Accelerators() {
		EnumSet<OrtProvider> providers = OrtEnvironment.getAvailableProviders();
		List<Device> available = new ArrayList<>();
		available.add(new Device("cpu", "CPU"));
		if (providers.contains(OrtProvider.CORE_ML)) {
			available.add(new Device("coreml", "CoreML (Apple GPU / Neural Engine)"));
		}
		if (providers.contains(OrtProvider.CUDA)) {
			available.add(new Device("cuda", "CUDA (NVIDIA GPU)"));
		}
		this.devices = List.copyOf(available);
	}

	@GetMapping("/api/accelerators")
	public List<Device> devices() {
		return devices;
	}

	/** The device id if this machine offers it, otherwise {@code cpu}. */
	public String resolve(String id) {
		return devices.stream().anyMatch(d -> d.id().equals(id)) ? id : "cpu";
	}

	public String label(String id) {
		return devices.stream().filter(d -> d.id().equals(id)).map(Device::label).findFirst().orElse("CPU");
	}

	/** The device id to use for a model: {@code cpu} when the model cannot run on the requested one. */
	public String resolve(String id, CoreMl coreMl) {
		String resolved = resolve(id);
		return "coreml".equals(resolved) && coreMl == CoreMl.UNSUPPORTED ? "cpu" : resolved;
	}

	public SessionConfigurer sessionOptions(String id) {
		return sessionOptions(id, CoreMl.NEURAL_NETWORK);
	}

	public SessionConfigurer sessionOptions(String id, CoreMl coreMl) {
		return switch (resolve(id, coreMl)) {
			case "coreml" -> coreMl == CoreMl.ML_PROGRAM
					? opts -> opts.addCoreML(Map.of("ModelFormat", "MLProgram"))
					: opts -> opts.addCoreML();
			case "cuda" -> opts -> opts.addCUDA(0);
			default -> opts -> {
			};
		};
	}

}
