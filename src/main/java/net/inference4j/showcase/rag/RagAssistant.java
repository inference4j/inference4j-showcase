package net.inference4j.showcase.rag;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.github.inference4j.audio.MoonshineRecognizer;
import io.github.inference4j.audio.SegmentedSpeechRecognizer;
import io.github.inference4j.audio.SileroVadDetector;
import io.github.inference4j.genai.ModelSources;
import io.github.inference4j.genai.nlp.TextGenerator;
import io.github.inference4j.langchain4j.Inference4jAudioDocumentParser;
import io.github.inference4j.langchain4j.Inference4jEmbeddingModel;
import io.github.inference4j.langchain4j.Inference4jOcrDocumentParser;
import io.github.inference4j.langchain4j.Inference4jPiiRedactor;
import io.github.inference4j.langchain4j.Inference4jPromptInjectionGuardrail;
import io.github.inference4j.langchain4j.Inference4jStreamingChatModel;
import io.github.inference4j.nlp.DebertaPromptInjectionDetector;
import io.github.inference4j.nlp.GlinerEntityRecognizer;
import io.github.inference4j.nlp.SentenceTransformerEmbedder;
import io.github.inference4j.vision.OcrPipeline;
import io.github.inference4j.vision.PaddleTextDetector;
import io.github.inference4j.vision.PaddleTextRecognizer;
import net.inference4j.showcase.ModelCache;
import net.inference4j.showcase.rag.RagExample.SourceFile;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Two small RAG pipelines on LangChain4j, every model in this JVM:
 *
 * <ul>
 * <li>{@link RagExample#CALLS}: recordings, speech-to-text, PII redaction, embeddings, Phi-4-mini.</li>
 * <li>{@link RagExample#PAPERWORK}: scans, OCR, PII redaction, embeddings, a prompt-injection guardrail,
 * Phi-4-mini.</li>
 * </ul>
 *
 * <p>Each question is answered on its own, without chat memory. Every model is capped at {@value #THREADS} threads.
 */
@Component
public class RagAssistant {

	static final int THREADS = 4;

	static final String LABEL = "label";

	static final String SHORT_LABEL = "short_label";

	static final String FILE_NAME = "file_name";

	/** Personal data labels. "address" is left out: it mostly matches the shop's own locations. */
	static final List<String> PII_LABELS = List.of("person", "email", "phone number", "date of birth",
			"credit card number", "bank account number", "social security number", "passport number", "ip address",
			"identity document number");

	/** The shop's own reference numbers look like ID numbers to the PII model; they are kept, not redacted. */
	private static final Pattern REFERENCE_NUMBER = Pattern.compile("(LB|DR|R)-\\d{4,5}");

	/** Audio is cut at pauses into pieces of at most this length, so answers can point to a moment. */
	private static final Duration AUDIO_PIECE = Duration.ofSeconds(8);

	/** Consecutive audio segments are joined up to this many characters before embedding. */
	private static final int AUDIO_WINDOW_CHARS = 300;

	private final ModelCache cache;

	private final Map<RagExample, Index> indexes = new EnumMap<>(RagExample.class);

	public RagAssistant(ModelCache cache) {
		this.cache = cache;
		for (RagExample example : RagExample.values()) {
			indexes.put(example, new Index());
		}
	}

	// ---------------------------------------------------------------- models

	private Inference4jEmbeddingModel embeddingModel() {
		return cache.get("rag-embedding", () -> Inference4jEmbeddingModel.builder()
			.embedder(SentenceTransformerEmbedder.builder()
				.modelId("inference4j/nomic-embed-text-v1.5")
				.normalize()
				.sessionOptions(o -> o.setIntraOpNumThreads(THREADS))
				.build())
			.queryPrefix("search_query: ")
			.documentPrefix("search_document: ")
			.build());
	}

	private Inference4jPiiRedactor redactor() {
		return cache.get("rag-pii", () -> Inference4jPiiRedactor.builder()
			.recognizer(GlinerEntityRecognizer.pii()
				.labels(PII_LABELS)
				.threshold(0.35f)
				.stride(64)
				.sessionOptions(o -> o.setIntraOpNumThreads(THREADS))
				.build())
			.keep(REFERENCE_NUMBER)
			.build());
	}

	private Inference4jAudioDocumentParser audioParser() {
		return cache.get("rag-audio", () -> Inference4jAudioDocumentParser.builder()
			.recognizer(new SegmentedSpeechRecognizer(
					MoonshineRecognizer.builder().sessionOptions(o -> o.setIntraOpNumThreads(THREADS)).build(),
					SileroVadDetector.builder().sessionOptions(o -> o.setIntraOpNumThreads(THREADS)).build(),
					AUDIO_PIECE))
			.mergeSegments(AUDIO_WINDOW_CHARS)
			.build());
	}

	private Inference4jOcrDocumentParser ocrParser() {
		return cache.get("rag-ocr", () -> new Inference4jOcrDocumentParser(OcrPipeline.builder()
			.detector(PaddleTextDetector.small().sessionOptions(o -> o.setIntraOpNumThreads(THREADS)).build())
			.recognizer(PaddleTextRecognizer.small().sessionOptions(o -> o.setIntraOpNumThreads(THREADS)).build())
			.build()));
	}

	private Inference4jPromptInjectionGuardrail guardrail() {
		return cache.get("rag-guardrail", () -> new Inference4jPromptInjectionGuardrail(
				DebertaPromptInjectionDetector.builder().sessionOptions(o -> o.setIntraOpNumThreads(THREADS)).build()));
	}

	private Inference4jStreamingChatModel chatModel() {
		return cache.get("rag-phi4", () -> new Inference4jStreamingChatModel(TextGenerator.builder()
			.model(ModelSources.phi4Mini())
			.maxLength(4096)
			.temperature(0) // greedy: answers grounded in the sources should not vary between runs
			.intraOpThreads(THREADS)
			.build()));
	}

	// ---------------------------------------------------------------- ingestion

	public Overview overview(RagExample example) {
		Index index = indexes.get(example);
		return new Overview(example.id(), example.files, index.processed, example.guarded);
	}

	/** Parses, redacts, embeds and stores every file of {@code example}, replacing what was stored before. */
	public void process(RagExample example, Consumer<String> status, Consumer<Processed> onFile) throws IOException {
		Index index = indexes.get(example);
		synchronized (index) {
			index.store.removeAll();
			index.processed = List.of();
			List<Processed> processed = new ArrayList<>();
			for (SourceFile file : example.files) {
				Processed result = example == RagExample.CALLS ? processCall(index, file, status)
						: processScan(index, file, status);
				processed.add(result);
				index.processed = List.copyOf(processed);
				onFile.accept(result);
			}
		}
	}

	private Processed processCall(Index index, SourceFile file, Consumer<String> status) throws IOException {
		status.accept("Transcribing " + file.name() + " (Moonshine + Silero VAD)");
		long start = System.nanoTime();
		List<Document> windows;
		try (InputStream in = open(RagExample.CALLS, file)) {
			windows = audioParser().parseSegments(in);
		}
		long parseMillis = millisSince(start);
		windows.forEach(window -> describe(window, file));
		return redactAndStore(index, file, windows, parseMillis, Map.of("segments", windows.size()), status);
	}

	private Processed processScan(Index index, SourceFile file, Consumer<String> status) throws IOException {
		status.accept("Reading " + file.name() + " with OCR (PP-OCRv6)");
		long start = System.nanoTime();
		Document parsed;
		try (InputStream in = open(RagExample.PAPERWORK, file)) {
			parsed = ocrParser().parse(in);
		}
		// Tick boxes come out of OCR as one-glyph lines; they carry no text worth embedding
		Document document = Document.from(parsed.text()
			.lines()
			.filter(line -> line.strip().length() > 1 || line.strip().matches("[A-Za-z0-9$]"))
			.collect(Collectors.joining("\n")), parsed.metadata());
		long parseMillis = millisSince(start);
		describe(document, file);
		Map<String, Object> details = Map.of(
				"lines", parsed.metadata().getInteger(Inference4jOcrDocumentParser.LINE_COUNT),
				"ocrConfidence", parsed.metadata().getDouble(Inference4jOcrDocumentParser.MEAN_CONFIDENCE));
		return redactAndStore(index, file, List.of(document), parseMillis, details, status);
	}

	private Processed redactAndStore(Index index, SourceFile file, List<Document> documents, long parseMillis,
			Map<String, Object> details, Consumer<String> status) {
		status.accept("Redacting personal data in " + file.name() + " (GLiNER PII)");
		long start = System.nanoTime();
		Inference4jPiiRedactor redactor = redactor();
		List<Document> redacted = documents.stream().map(redactor::transform).toList();
		long redactMillis = millisSince(start);
		int redactions = redacted.stream()
			.map(document -> document.metadata().getInteger(Inference4jPiiRedactor.REDACTED_COUNT))
			.mapToInt(count -> count == null ? 0 : count)
			.sum();

		status.accept("Embedding " + file.name() + " (nomic-embed-text-v1.5)");
		start = System.nanoTime();
		List<TextSegment> segments = redacted.stream().map(Document::toTextSegment).toList();
		index.store.addAll(embeddingModel().embedAll(segments).content(), segments);
		long embedMillis = millisSince(start);

		return new Processed(file.name(), file.label(), pieces(documents), pieces(redacted), redactions, details,
				parseMillis, redactMillis, embedMillis);
	}

	private static void describe(Document document, SourceFile file) {
		Long startMs = document.metadata().getLong(Inference4jAudioDocumentParser.START_MS);
		document.metadata()
			.put(FILE_NAME, file.name())
			.put(LABEL, startMs == null ? file.label() : file.label() + ", from " + clock(startMs))
			.put(SHORT_LABEL, startMs == null ? file.shortLabel() : file.shortLabel() + " " + clock(startMs));
	}

	private static List<Piece> pieces(List<Document> documents) {
		return documents.stream()
			.map(document -> new Piece(document.metadata().getLong(Inference4jAudioDocumentParser.START_MS),
					document.text()))
			.toList();
	}

	// ---------------------------------------------------------------- questions

	/**
	 * Answers one question with {@code AiServices}: retrieval, the guardrail (paperwork only), then Phi-4-mini. Steps
	 * are reported to {@code listener} as they happen.
	 */
	public void ask(RagExample example, String question, AskListener listener) {
		Index index = indexes.get(example);
		if (index.processed.size() < example.files.size()) {
			throw new IllegalStateException("Process the files first");
		}
		Inference4jEmbeddingModel embeddingModel = embeddingModel();
		ContentRetriever storeRetriever = EmbeddingStoreContentRetriever.builder()
			.embeddingStore(index.store)
			.embeddingModel(embeddingModel)
			.embeddingInputType(EmbeddingInputType.QUERY)
			.maxResults(example.maxResults)
			.build();
		ContentRetriever retriever = query -> {
			long start = System.nanoTime();
			List<Content> contents = storeRetriever.retrieve(query);
			listener.retrieval(new RetrievalStep(contents.stream().map(RagAssistant::passage).toList(),
					millisSince(start)));
			return contents;
		};

		AiServices<Assistant> builder = AiServices.builder(Assistant.class)
			.streamingChatModel(chatModel())
			.systemMessageProvider(memoryId -> example.systemPrompt)
			.retrievalAugmentor(DefaultRetrievalAugmentor.builder()
				.contentRetriever(retriever)
				.contentInjector((contents, message) -> {
					UserMessage injected = UserMessage.from(prompt(example, ((UserMessage) message).singleText(),
							contents));
					listener.prompt(new PromptStep(example.systemPrompt, injected.singleText()));
					return injected;
				})
				.build());
		if (example.guarded) {
			Inference4jPromptInjectionGuardrail guardrail = guardrail();
			builder.inputGuardrails(new InputGuardrail() {

				@Override
				public InputGuardrailResult validate(InputGuardrailRequest request) {
					long start = System.nanoTime();
					InputGuardrailResult result = guardrail.validate(request);
					String reason = result.isSuccess() ? null : result.failures().get(0).message();
					listener.guardrail(new GuardrailStep(result.isSuccess(), reason, millisSince(start)));
					return result;
				}
			});
		}

		CompletableFuture<ChatResponse> done = new CompletableFuture<>();
		long[] generationStart = new long[1];
		try {
			// AiServices runs retrieval, then the input guardrails, before it calls the chat model; a blocked
			// message throws here and the chat model is never called
			builder.build()
				.chat(question)
				.onPartialResponse(token -> {
					if (generationStart[0] == 0) {
						generationStart[0] = System.nanoTime();
					}
					listener.token(token);
				})
				.onCompleteResponse(done::complete)
				.onError(done::completeExceptionally)
				.start();
		}
		catch (InputGuardrailException e) {
			listener.blocked();
			return;
		}
		try {
			ChatResponse response = done.get(10, TimeUnit.MINUTES);
			Integer tokens = response.tokenUsage() == null ? null : response.tokenUsage().outputTokenCount();
			long millis = generationStart[0] == 0 ? 0 : millisSince(generationStart[0]);
			listener.answer(new AnswerStep(tokens == null ? 0 : tokens, millis));
		}
		catch (ExecutionException e) {
			throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", e);
		}
		catch (TimeoutException e) {
			throw new IllegalStateException("The answer took too long", e);
		}
	}

	/** The user message the chat model sees: the question, then the retrieved sources with their labels. */
	private static String prompt(RagExample example, String question, List<Content> contents) {
		return "Her question: " + question + "\n\n" + example.heading + ":\n" + contents.stream()
			.map(content -> "- " + content.textSegment().metadata().getString(LABEL) + ": "
					+ content.textSegment().text().replaceAll("\\s*\\n\\s*", "\n  ").strip())
			.collect(Collectors.joining("\n"));
	}

	private static Passage passage(Content content) {
		TextSegment segment = content.textSegment();
		Object score = content.metadata().get(ContentMetadata.SCORE);
		return new Passage(segment.text(), segment.metadata().getString(FILE_NAME),
				segment.metadata().getString(SHORT_LABEL),
				segment.metadata().getLong(Inference4jAudioDocumentParser.START_MS),
				score instanceof Number number ? number.doubleValue() : 0);
	}

	// ---------------------------------------------------------------- helpers and types

	private static InputStream open(RagExample example, SourceFile file) throws IOException {
		return new ClassPathResource("rag/" + example.folder + "/" + file.name()).getInputStream();
	}

	/** One of the example files, for the page to play or show. */
	public Resource file(RagExample example, String name) {
		return new ClassPathResource("rag/" + example.folder + "/" + example.file(name).name());
	}

	private static long millisSince(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	static String clock(long millis) {
		long seconds = millis / 1000;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	interface Assistant {

		TokenStream chat(String question);

	}

	private static final class Index {

		final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

		volatile List<Processed> processed = List.of();

	}

	public record Overview(String example, List<SourceFile> files, List<Processed> processed, boolean guarded) {
	}

	/** A piece of parsed text; {@code startMs} is set for audio. */
	public record Piece(Long startMs, String text) {
	}

	public record Processed(String name, String label, List<Piece> original, List<Piece> redacted, int redactions,
			Map<String, Object> details, long parseMillis, long redactMillis, long embedMillis) {
	}

	public record Passage(String text, String fileName, String citation, Long startMs, double score) {
	}

	public record GuardrailStep(boolean passed, String reason, long millis) {
	}

	public record RetrievalStep(List<Passage> passages, long millis) {
	}

	public record PromptStep(String system, String user) {
	}

	public record AnswerStep(int tokens, long millis) {
	}

	/** Receives the steps of {@link #ask(RagExample, String, AskListener)} as they happen. */
	public interface AskListener {

		void guardrail(GuardrailStep step);

		void blocked();

		void retrieval(RetrievalStep step);

		void prompt(PromptStep step);

		void token(String token);

		void answer(AnswerStep step);

	}

}
