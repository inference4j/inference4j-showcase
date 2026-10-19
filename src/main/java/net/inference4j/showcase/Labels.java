package net.inference4j.showcase;

import java.util.List;

/** Cleans the entity or class labels a page sends: trimmed, non-empty, no duplicates. */
final class Labels {

	private Labels() {
	}

	static List<String> clean(List<String> labels) {
		return labels == null ? List.of()
				: labels.stream().map(String::strip).filter(l -> !l.isEmpty()).distinct().toList();
	}

}
