package net.inference4j.showcase.rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.genai.ModelSources;
import io.github.inference4j.genai.nlp.TextGenerator;

/**
 * A LangChain4j {@link StreamingChatModel} backed by Phi-4-mini running in-process through onnxruntime-genai.
 *
 * <p>The conversation is formatted here with Phi-4's chat template, so the generator gets a pass-through template.
 * Tool calling is not supported.
 *
 * <p>{@code TextGenerator} has no thread setting, so the model is loaded from a directory of links to the cached
 * model files whose {@code genai_config.json} caps ONNX Runtime at {@code threads} threads.
 */
public class LocalPhiChatModel implements StreamingChatModel, AutoCloseable {

	private static final String MODEL_NAME = "phi-4-mini-instruct";

	private final TextGenerator generator;

	public LocalPhiChatModel(int maxLength, int threads) {
		Path capped = cappedModelDirectory(ModelSources.phi4Mini().modelSource().resolve("model"), threads);
		this.generator = TextGenerator.builder()
			.modelSource(id -> capped)
			.chatTemplate(prompt -> prompt)
			.maxLength(maxLength)
			.temperature(0) // greedy: answers grounded in the sources should not vary between runs
			.build();
	}

	@Override
	public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
		if (request.toolSpecifications() != null && !request.toolSpecifications().isEmpty()) {
			handler.onError(new UnsupportedOperationException("LocalPhiChatModel does not support tool calling"));
			return;
		}
		try {
			GenerationResult result = generator.generate(format(request), handler::onPartialResponse);
			handler.onCompleteResponse(ChatResponse.builder()
				.aiMessage(AiMessage.from(result.text().strip()))
				.tokenUsage(new TokenUsage(null, result.generatedTokens()))
				.finishReason(FinishReason.STOP)
				.modelName(MODEL_NAME)
				.build());
		}
		catch (RuntimeException e) {
			handler.onError(e);
		}
	}

	/** Formats the conversation with Phi-4's template, ending with an open assistant turn. */
	static String format(ChatRequest request) {
		StringBuilder prompt = new StringBuilder();
		for (ChatMessage message : request.messages()) {
			if (message instanceof SystemMessage system) {
				prompt.append("<|system|>").append(system.text()).append("<|end|>");
			}
			else if (message instanceof UserMessage user) {
				prompt.append("<|user|>").append(user.singleText()).append("<|end|>");
			}
			else if (message instanceof AiMessage ai && ai.text() != null) {
				prompt.append("<|assistant|>").append(ai.text()).append("<|end|>");
			}
		}
		return prompt.append("<|assistant|>").toString();
	}

	@Override
	public void close() {
		generator.close();
	}

	private static Path cappedModelDirectory(Path modelDirectory, int threads) {
		try {
			Path capped = Path.of(System.getProperty("java.io.tmpdir"), "inference4j-showcase",
					MODEL_NAME + "-" + threads + "-threads");
			Files.createDirectories(capped);
			try (Stream<Path> files = Files.list(modelDirectory)) {
				for (Path file : files.toList()) {
					String name = file.getFileName().toString();
					Path target = capped.resolve(name);
					if (name.equals("genai_config.json")) {
						Files.writeString(target, withThreads(Files.readString(file), threads));
					}
					else if (!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
						Files.createSymbolicLink(target, file.toAbsolutePath());
					}
				}
			}
			return capped;
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not prepare the Phi-4-mini model directory", e);
		}
	}

	/** Adds {@code "intra_op_num_threads"} to the session options of a genai_config.json. */
	static String withThreads(String config, int threads) {
		if (config.contains("\"intra_op_num_threads\"")) {
			return config.replaceAll("\"intra_op_num_threads\"\\s*:\\s*\\d+", "\"intra_op_num_threads\": " + threads);
		}
		return config.replaceFirst("\"provider_options\"\\s*:\\s*\\[\\s*]",
				"\"provider_options\": [], \"intra_op_num_threads\": " + threads);
	}

}
