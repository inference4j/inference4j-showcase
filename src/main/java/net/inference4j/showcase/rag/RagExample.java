package net.inference4j.showcase.rag;

import java.util.List;
import java.util.Locale;

/**
 * The two RAG examples. Each one indexes one kind of source and answers single questions from it.
 */
public enum RagExample {

	CALLS("calls", "What was said in her calls", 3, false, """
			You are a support assistant at Lakeside Bike Rentals. A customer asks a question about her earlier phone \
			calls with the shop. Answer her directly as "you", in one or two sentences, using only what was said in \
			the calls below her question. If the calls do not answer it, say that you could not find it in her \
			calls. Some personal details were removed for privacy; never guess them.""",
			List.of(new SourceFile("call-2026-10-29.wav", "Phone call on 29 October 2026", "29 Oct call"),
					new SourceFile("call-2026-11-02.wav", "Phone call on 2 November 2026", "2 Nov call"),
					new SourceFile("call-2026-11-04.wav", "Phone call on 4 November 2026", "4 Nov call"))),

	PAPERWORK("scans", "Her rental forms", 2, true, """
			You are a support assistant at Lakeside Bike Rentals. A customer asks a question about the paperwork of \
			her rental. Answer her directly as "you", in one to three sentences, using only the forms below her \
			question. If the forms do not answer it, say that you could not find it in her paperwork. Some personal \
			details were removed for privacy; never guess them.""",
			List.of(new SourceFile("pickup-checklist-2026-10-31.jpg", "Pickup checklist, 31 October 2026",
					"Pickup checklist"),
					new SourceFile("rental-receipt-R-10388.jpg", "Rental receipt R-10388, 31 October 2026",
							"Rental receipt"),
					new SourceFile("damage-report-DR-2291.jpg", "Damage report DR-2291, 2 November 2026",
							"Damage report")));

	/** Classpath folder under {@code rag/} holding the files. */
	final String folder;

	/** Heading of the retrieved sources in the prompt. */
	final String heading;

	/** Segments passed to the chat model. */
	final int maxResults;

	/** Whether user messages go through the prompt-injection guardrail. */
	final boolean guarded;

	final String systemPrompt;

	final List<SourceFile> files;

	RagExample(String folder, String heading, int maxResults, boolean guarded, String systemPrompt,
			List<SourceFile> files) {
		this.folder = folder;
		this.heading = heading;
		this.maxResults = maxResults;
		this.guarded = guarded;
		this.systemPrompt = systemPrompt;
		this.files = files;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	static RagExample of(String id) {
		for (RagExample example : values()) {
			if (example.id().equals(id)) {
				return example;
			}
		}
		throw new IllegalArgumentException("Unknown example: " + id);
	}

	SourceFile file(String name) {
		return files.stream()
			.filter(file -> file.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown file: " + name));
	}

	/**
	 * A file of an example. {@code label} names it in the prompt; {@code shortLabel} names it on source chips.
	 */
	public record SourceFile(String name, String label, String shortLabel) {
	}

}
