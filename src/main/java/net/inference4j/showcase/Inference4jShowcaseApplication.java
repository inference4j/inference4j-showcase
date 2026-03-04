package net.inference4j.showcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Inference4jShowcaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(Inference4jShowcaseApplication.class, args);
	}

}
