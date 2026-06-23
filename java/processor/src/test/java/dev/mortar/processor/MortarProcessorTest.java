package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Set;

final class MortarProcessorTest {
    @Test
    void processorDeclaresMortarEntityAnnotation() {
        MortarProcessor processor = new MortarProcessor();

        assertThat(processor.getSupportedAnnotationTypes())
            .containsExactly("*");
    }

    @Test
    void processorDoesNotClaimRoundsYet() {
        MortarProcessor processor = new MortarProcessor();

        assertThat(processor.process(Set.of(), null)).isFalse();
        assertThat(processor.getSupportedSourceVersion().name()).isEqualTo("RELEASE_25");
    }

    @Test
    void annotationDeclaresTableMetadataContract() throws NoSuchMethodException {
        assertThat(MortarEntity.class.getDeclaredMethod("table").getReturnType()).isEqualTo(String.class);
        assertThat(MortarEntity.class.getDeclaredMethod("alias").getDefaultValue()).isEqualTo("");
    }
}
