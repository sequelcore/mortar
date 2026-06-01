package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

final class MortarAnnotationContractTest {
    @Test
    void columnAnnotationDeclaresColumnMetadata() throws NoSuchMethodException {
        assertThat(MortarColumn.class.getDeclaredMethod("name").getReturnType()).isEqualTo(String.class);
        assertThat(MortarColumn.class.getDeclaredMethod("nullable").getDefaultValue()).isEqualTo(true);
        assertThat(MortarColumn.class.getAnnotation(java.lang.annotation.Retention.class).value())
            .isEqualTo(RetentionPolicy.SOURCE);
        assertThat(MortarColumn.class.getAnnotation(java.lang.annotation.Target.class).value())
            .containsExactly(ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD);
    }

    @Test
    void idAnnotationMarksIdentityFields() {
        assertThat(MortarId.class.getAnnotation(java.lang.annotation.Retention.class).value())
            .isEqualTo(RetentionPolicy.SOURCE);
        assertThat(MortarId.class.getAnnotation(java.lang.annotation.Target.class).value())
            .containsExactly(ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD);
    }

    @Test
    void relationAnnotationDeclaresRelationshipMetadata() throws NoSuchMethodException {
        assertThat(MortarRelation.class.getDeclaredMethod("target").getReturnType()).isEqualTo(Class.class);
        assertThat(MortarRelation.class.getDeclaredMethod("type").getDefaultValue()).isEqualTo(MortarRelationType.MANY_TO_ONE);
        assertThat(MortarRelation.class.getDeclaredMethod("localColumn").getReturnType()).isEqualTo(String.class);
        assertThat(MortarRelation.class.getDeclaredMethod("targetColumn").getDefaultValue()).isEqualTo("id");
    }
}
