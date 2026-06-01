package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class MortarProcessorGradleMetadataTest {
    @Test
    void declaresIncrementalAnnotationProcessorMetadata() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("META-INF/gradle/incremental.annotation.processors")) {

            assertThat(stream).isNotNull();
            String metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(metadata).contains("dev.mortar.processor.MortarProcessor,isolating");
        }
    }
}
