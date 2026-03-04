package net.inference4j.showcase;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ModelCache {

	private static final Logger log = LoggerFactory.getLogger(ModelCache.class);

	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
	private final long idleTimeoutNanos;

	public ModelCache(@Value("${showcase.model-cache.idle-timeout:10m}") Duration idleTimeout) {
		this.idleTimeoutNanos = idleTimeout.toNanos();
		log.info("ModelCache initialized with idle timeout: {}", idleTimeout);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, java.util.function.Supplier<T> factory) {
		CacheEntry entry = cache.compute(key, (k, existing) -> {
			if (existing != null) {
				existing.touch();
				return existing;
			}
			log.info("Loading model: {}", k);
			Object model = factory.get();
			log.info("Model loaded: {}", k);
			return new CacheEntry(model);
		});
		return (T) entry.model;
	}

	@Scheduled(fixedDelay = 60_000)
	void evictStale() {
		long now = System.nanoTime();
		for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
			String key = entry.getKey();
			cache.compute(key, (k, existing) -> {
				if (existing == null) {
					return null;
				}
				if (now - existing.lastAccessed > idleTimeoutNanos) {
					log.info("Evicting idle model: {}", k);
					closeQuietly(existing.model);
					return null;
				}
				return existing;
			});
		}
	}

	@PreDestroy
	void closeAll() {
		cache.forEach((key, entry) -> {
			log.info("Closing model: {}", key);
			closeQuietly(entry.model);
		});
		cache.clear();
	}

	private static void closeQuietly(Object model) {
		if (model instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			}
			catch (Exception e) {
				log.warn("Error closing model", e);
			}
		}
	}

	private static class CacheEntry {
		final Object model;
		volatile long lastAccessed;

		CacheEntry(Object model) {
			this.model = model;
			this.lastAccessed = System.nanoTime();
		}

		void touch() {
			this.lastAccessed = System.nanoTime();
		}
	}

}
