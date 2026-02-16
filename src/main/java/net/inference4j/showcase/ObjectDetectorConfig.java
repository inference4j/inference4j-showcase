package net.inference4j.showcase;

import io.github.inference4j.vision.ObjectDetector;
import io.github.inference4j.vision.Yolo26Detector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObjectDetectorConfig {

	@Bean
	ObjectDetector objectDetector() {
		return Yolo26Detector.builder().build();
	}

}
