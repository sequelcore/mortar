package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Set;

final class MortarProcessorTest {
    @Test
    void processorDeclaresMortarEntityAnnotation() {
        MortarProcessor processor = new MortarProcessor();

        assertThat(processor.getSupportedAnnotationTypes())
            .containsExactlyInAnyOrder(
                "dev.mortar.processor.MortarColumn",
                "dev.mortar.processor.MortarEntity",
                "dev.mortar.processor.MortarId",
                "dev.mortar.processor.MortarRelation",
                "jakarta.persistence.Column",
                "jakarta.persistence.Entity",
                "jakarta.persistence.Id",
                "jakarta.persistence.JoinColumn",
                "jakarta.persistence.Table"
            );
    }

    @Test
    void processorDoesNotClaimRoundsYet() {
        MortarProcessor processor = new MortarProcessor();

        assertThat(processor.process(Set.of(), null)).isFalse();
        assertThat(processor.getSupportedSourceVersion().name()).isEqualTo("RELEASE_21");
    }

    @Test
    void annotationDeclaresTableMetadataContract() throws NoSuchMethodException {
        assertThat(MortarEntity.class.getDeclaredMethod("table").getReturnType()).isEqualTo(String.class);
        assertThat(MortarEntity.class.getDeclaredMethod("alias").getDefaultValue()).isEqualTo("");
    }
}
