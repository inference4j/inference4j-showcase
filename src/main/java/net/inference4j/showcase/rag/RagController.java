package net.inference4j.showcase.rag;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import net.inference4j.showcase.rag.RagAssistant.AnswerStep;
import net.inference4j.showcase.rag.RagAssistant.AskListener;
import net.inference4j.showcase.rag.RagAssistant.GuardrailStep;
import net.inference4j.showcase.rag.RagAssistant.Overview;
import net.inference4j.showcase.rag.RagAssistant.PromptStep;
import net.inference4j.showcase.rag.RagAssistant.RetrievalStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG assistant examples. Long-running calls stream server-sent events, one per pipeline step, with JSON data.
 */
@RestController
@RequestMapping("/api/rag/{example}")
public class RagController {

	private static final Logger log = LoggerFactory.getLogger(RagController.class);

	private final RagAssistant assistant;

	// One worker: every model already uses several threads, and Phi-4-mini answers one question at a time
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public RagController(RagAssistant assistant) {
		this.assistant = assistant;
	}

	record AskRequest(String question) {
	}

	@GetMapping
	public Overview overview(@PathVariable String example) {
		return assistant.overview(example(example));
	}

	@GetMapping("/files/{name:.+}")
	public ResponseEntity<Resource> file(@PathVariable String example, @PathVariable String name) {
		try {
			// A Resource with a known length lets Spring answer range requests, so the page can seek in a recording
			Resource resource = assistant.file(example(example), name);
			MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
			return ResponseEntity.ok().contentType(type).body(resource);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such file");
		}
	}

	@PostMapping(value = "/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter process(@PathVariable String example) {
		RagExample ragExample = example(example);
		return stream(events -> assistant.process(ragExample, events::status,
				processed -> events.send("file", processed)));
	}

	@PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter ask(@PathVariable String example, @RequestBody AskRequest request) {
		RagExample ragExample = example(example);
		String question = request.question() == null ? "" : request.question().strip();
		if (question.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type a question");
		}
		return stream(events -> assistant.ask(ragExample, question, new AskListener() {

			@Override
			public void guardrail(GuardrailStep step) {
				events.send("guardrail", step);
			}

			@Override
			public void blocked() {
				events.send("blocked", Map.of());
			}

			@Override
			public void retrieval(RetrievalStep step) {
				events.send("retrieval", step);
			}

			@Override
			public void prompt(PromptStep step) {
				events.send("prompt", step);
			}

			@Override
			public void token(String token) {
				events.send("token", Map.of("text", token));
			}

			@Override
			public void answer(AnswerStep step) {
				events.send("answer", step);
			}
		}));
	}

	private static RagExample example(String id) {
		try {
			return RagExample.of(id);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	/** Runs {@code work} on the executor, then sends {@code done}; a failure becomes an {@code error} event. */
	private SseEmitter stream(Work work) {
		SseEmitter emitter = new SseEmitter(900_000L);
		Events events = new Events(emitter);
		executor.submit(() -> {
			try {
				work.run(events);
				events.send("done", Map.of());
			}
			catch (Exception e) {
				events.error(e);
			}
			finally {
				emitter.complete();
			}
		});
		return emitter;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	@FunctionalInterface
	private interface Work {

		void run(Events events) throws Exception;

	}

	private record Events(SseEmitter emitter) {

		void send(String name, Object data) {
			try {
				emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
			}
			catch (IOException e) {
				throw new IllegalStateException("Client disconnected", e);
			}
		}

		void status(String message) {
			send("status", Map.of("message", message));
		}

		void error(Throwable error) {
			log.warn("RAG request failed", error);
			String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
			try {
				emitter.send(SseEmitter.event().name("error").data(Map.of("message", message), MediaType.APPLICATION_JSON));
			}
			catch (IOException | IllegalStateException ignored) {
				// client already gone
			}
		}

	}

}
