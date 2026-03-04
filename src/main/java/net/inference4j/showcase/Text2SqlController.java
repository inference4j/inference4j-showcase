package net.inference4j.showcase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.inference4j.generation.GenerationResult;
import io.github.inference4j.nlp.T5SqlGenerator;

import jakarta.annotation.PreDestroy;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/text2sql")
public class Text2SqlController {

	private static final Set<String> KNOWN_MODELS = Set.of("t5-small", "t5-large");

	// Compact DDL schema for t5-small-awesome-text-to-sql
	private static final String SCHEMA_DDL = String.join(" ",
		"CREATE TABLE Artist (ArtistId INTEGER, Name VARCHAR(120));",
		"CREATE TABLE Genre (GenreId INTEGER, Name VARCHAR(120));",
		"CREATE TABLE MediaType (MediaTypeId INTEGER, Name VARCHAR(120));",
		"CREATE TABLE Playlist (PlaylistId INTEGER, Name VARCHAR(120));",
		"CREATE TABLE Employee (EmployeeId INTEGER, LastName VARCHAR(20), FirstName VARCHAR(20), Title VARCHAR(30), ReportsTo INTEGER, BirthDate TIMESTAMP, HireDate TIMESTAMP, Address VARCHAR(70), City VARCHAR(40), State VARCHAR(40), Country VARCHAR(40), PostalCode VARCHAR(10), Phone VARCHAR(24), Fax VARCHAR(24), Email VARCHAR(60));",
		"CREATE TABLE Album (AlbumId INTEGER, Title VARCHAR(160), ArtistId INTEGER);",
		"CREATE TABLE Customer (CustomerId INTEGER, FirstName VARCHAR(40), LastName VARCHAR(20), Company VARCHAR(80), Address VARCHAR(70), City VARCHAR(40), State VARCHAR(40), Country VARCHAR(40), PostalCode VARCHAR(10), Phone VARCHAR(24), Fax VARCHAR(24), Email VARCHAR(60), SupportRepId INTEGER);",
		"CREATE TABLE Invoice (InvoiceId INTEGER, CustomerId INTEGER, InvoiceDate TIMESTAMP, BillingAddress VARCHAR(70), BillingCity VARCHAR(40), BillingState VARCHAR(40), BillingCountry VARCHAR(40), BillingPostalCode VARCHAR(10), Total NUMERIC(10,2));",
		"CREATE TABLE Track (TrackId INTEGER, Name VARCHAR(200), AlbumId INTEGER, MediaTypeId INTEGER, GenreId INTEGER, Composer VARCHAR(220), Milliseconds INTEGER, Bytes INTEGER, UnitPrice NUMERIC(10,2));",
		"CREATE TABLE InvoiceLine (InvoiceLineId INTEGER, InvoiceId INTEGER, TrackId INTEGER, UnitPrice NUMERIC(10,2), Quantity INTEGER);",
		"CREATE TABLE PlaylistTrack (PlaylistId INTEGER, TrackId INTEGER);"
	);

	// Spider format schema for t5-large-spider
	private static final String SCHEMA_SPIDER = String.join(" [SEP] ",
		"\"Artist\" \"ArtistId\" int, \"Name\" varchar",
		"\"Genre\" \"GenreId\" int, \"Name\" varchar",
		"\"MediaType\" \"MediaTypeId\" int, \"Name\" varchar",
		"\"Playlist\" \"PlaylistId\" int, \"Name\" varchar",
		"\"Employee\" \"EmployeeId\" int, \"LastName\" varchar, \"FirstName\" varchar, \"Title\" varchar, \"ReportsTo\" int, \"BirthDate\" timestamp, \"HireDate\" timestamp, \"Address\" varchar, \"City\" varchar, \"State\" varchar, \"Country\" varchar, \"PostalCode\" varchar, \"Phone\" varchar, \"Fax\" varchar, \"Email\" varchar",
		"\"Album\" \"AlbumId\" int, \"Title\" varchar, \"ArtistId\" int",
		"\"Customer\" \"CustomerId\" int, \"FirstName\" varchar, \"LastName\" varchar, \"Company\" varchar, \"Address\" varchar, \"City\" varchar, \"State\" varchar, \"Country\" varchar, \"PostalCode\" varchar, \"Phone\" varchar, \"Fax\" varchar, \"Email\" varchar, \"SupportRepId\" int",
		"\"Invoice\" \"InvoiceId\" int, \"CustomerId\" int, \"InvoiceDate\" timestamp, \"BillingAddress\" varchar, \"BillingCity\" varchar, \"BillingState\" varchar, \"BillingCountry\" varchar, \"BillingPostalCode\" varchar, \"Total\" numeric",
		"\"Track\" \"TrackId\" int, \"Name\" varchar, \"AlbumId\" int, \"MediaTypeId\" int, \"GenreId\" int, \"Composer\" varchar, \"Milliseconds\" int, \"Bytes\" int, \"UnitPrice\" numeric",
		"\"InvoiceLine\" \"InvoiceLineId\" int, \"InvoiceId\" int, \"TrackId\" int, \"UnitPrice\" numeric, \"Quantity\" int",
		"\"PlaylistTrack\" \"PlaylistId\" int, \"TrackId\" int"
	);

	private final ModelCache cache;
	private final JdbcTemplate jdbc;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public Text2SqlController(ModelCache cache, JdbcTemplate jdbc) {
		this.cache = cache;
		this.jdbc = jdbc;
	}

	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter generate(@RequestBody GenerateRequest request) {
		String model = request.model() != null ? request.model() : "t5-small";
		if (!KNOWN_MODELS.contains(model)) {
			throw new IllegalArgumentException("Unknown model: " + model);
		}

		SseEmitter emitter = new SseEmitter(300_000L);

		executor.submit(() -> {
			try {
				T5SqlGenerator generator = cache.get("t5sql-" + model, () -> buildGenerator(model));
				String schema = "t5-large".equals(model) ? SCHEMA_SPIDER : SCHEMA_DDL;
				GenerationResult result = generator.generateSql(request.question(), schema, token -> {
					try {
						emitter.send(SseEmitter.event().name("token").data(token));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
				emitter.send(SseEmitter.event().name("done").data(
					Map.of("sql", result.text(),
						   "tokenCount", result.generatedTokens(),
						   "durationMillis", result.duration().toMillis())
				));
				emitter.complete();
			} catch (Exception e) {
				try {
					emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
				} catch (Exception ignored) {
				}
				emitter.completeWithError(e);
			}
		});

		return emitter;
	}

	@PostMapping("/execute")
	public List<Map<String, Object>> execute(@RequestBody ExecuteRequest request) {
		String sql = request.sql().strip();
		if (!sql.toUpperCase().startsWith("SELECT")) {
			throw new IllegalArgumentException("Only SELECT statements are allowed");
		}
		return jdbc.queryForList(sql);
	}

	private T5SqlGenerator buildGenerator(String model) {
		T5SqlGenerator.Builder builder = switch (model) {
			case "t5-large" -> T5SqlGenerator.t5LargeSpider();
			default -> T5SqlGenerator.t5SmallAwesome();
		};
		return builder.maxNewTokens(256).build();
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	record GenerateRequest(String question, String model) {}

	record ExecuteRequest(String sql) {}

}
