package net.inference4j.showcase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final LiveTranscriptionHandler liveTranscription;

	public WebSocketConfig(LiveTranscriptionHandler liveTranscription) {
		this.liveTranscription = liveTranscription;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(liveTranscription, "/ws/live-transcription");
	}

	/** The browser sends about 100 ms of 48 kHz float audio per message (~19 KB), above the 8 KB default. */
	@Bean
	public ServletServerContainerFactoryBean webSocketContainer() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxBinaryMessageBufferSize(512 * 1024);
		container.setMaxTextMessageBufferSize(64 * 1024);
		container.setMaxSessionIdleTimeout(10 * 60 * 1000L);
		return container;
	}

}
