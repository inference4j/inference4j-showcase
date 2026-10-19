package net.inference4j.showcase;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// A real server: WebSocketConfig's container factory needs the servlet container's WebSocket support
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Inference4jShowcaseApplicationTests {

	@Test
	void contextLoads() {
	}

}
