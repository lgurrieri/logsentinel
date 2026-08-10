package com.logsentinel.infrastructure.adapters.in.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple source-inspection guard for the LOG-US1-BE-02B acceptance criterion:
 * the web layer must never interact with or expose the pure JPA persistence entity
 * ({@code IncidentJpaEntity}).
 *
 * <p>Deliberately NOT reflection-based and NOT a framework (e.g. ArchUnit): it just reads
 * every {@code .java} source file under {@code infrastructure/adapters/in/web/**}
 * (controller + DTOs) as plain text and fails if any of them mentions
 * {@code IncidentJpaEntity} at all (import, fully qualified reference, or otherwise).
 */
class IncidentControllerNoJpaEntityImportTest {

    private static final String FORBIDDEN_TOKEN = "IncidentJpaEntity";

    private static final Path WEB_LAYER_SOURCE_ROOT =
            Path.of("src/main/java/com/logsentinel/infrastructure/adapters/in/web");

    @Test
    void web_layer_sources_should_never_reference_incident_jpa_entity() {
        List<Path> webLayerSources = listJavaFiles(WEB_LAYER_SOURCE_ROOT);

        assertThat(webLayerSources)
                .as("sanity check: the web layer source directory must contain .java files")
                .isNotEmpty();

        for (Path sourceFile : webLayerSources) {
            String content = readFile(sourceFile);
            assertThat(content)
                    .as("%s must never reference the JPA persistence entity '%s'",
                            sourceFile.getFileName(), FORBIDDEN_TOKEN)
                    .doesNotContain(FORBIDDEN_TOKEN);
        }
    }

    private static List<Path> listJavaFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
