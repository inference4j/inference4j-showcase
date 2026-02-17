# inference4j showcase

Interactive demos of [inference4j](https://github.com/inference4j/inference4j) — Java AI inference powered by ONNX Runtime.

## Demos

| Demo | Model | Domain |
|------|-------|--------|
| Sentiment Analysis | DistilBERT (SST-2) | NLP |
| Image Classification | ResNet-50 / EfficientNet | Vision |
| Object Detection | YOLO26 (COCO, 80 classes) | Vision |
| Text Detection | CRAFT | Vision |
| Speech to Text | Wav2Vec2 | Audio |
| Voice Activity Detection | Silero VAD | Audio |

## Prerequisites

- Java 17+
- Gradle 9.3+

## Running

```bash
./gradlew bootRun
```

Then open http://localhost:8080.

On first startup, models are downloaded automatically from Hugging Face and cached locally. This may take a few minutes depending on your connection.

### Running from an IDE

inference4j uses ONNX Runtime which requires native access. When running via `./gradlew bootRun`, the JVM flag is set automatically. When running the `main()` method directly from an IDE (IntelliJ, Eclipse, etc.), you need to add this VM option to your run configuration:

```
--enable-native-access=ALL-UNNAMED
```

In IntelliJ: **Run > Edit Configurations > Modify options > Add VM options**, then paste the flag.

Alternatively, configure your IDE to delegate run/debug to Gradle (**Settings > Build, Execution, Deployment > Build Tools > Gradle > Build and run using: Gradle**), which picks up the `bootRun` JVM args automatically.

## Configuration

Models are configured in `application.yml`. Each model is opt-in — disable any you don't need:

```yaml
inference4j:
  nlp:
    text-classifier:
      enabled: true
  vision:
    text-detector:
      enabled: true
  audio:
    speech-recognizer:
      enabled: true
    vad:
      enabled: true
```

Image classification and object detection are configured manually via `@Configuration` classes (see `ImageClassifierConfig` and `ObjectDetectorConfig`) since they use models not covered by the starter's defaults.

## Stack

- Spring Boot 4.0
- [inference4j](https://github.com/inference4j/inference4j) (Spring Boot starter + tasks)
- Vanilla HTML/CSS/JS (no frontend framework)
