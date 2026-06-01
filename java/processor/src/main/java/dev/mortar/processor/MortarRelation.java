package dev.mortar.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares relationship metadata used to generate refactor-safe join paths.
 */
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD })
@Retention(RetentionPolicy.SOURCE)
public @interface MortarRelation {
    /**
     * Target entity type.
     *
     * @return target entity type
     */
    Class<?> target();

    /**
     * Relationship cardinality metadata.
     *
     * @return relationship type
     */
    MortarRelationType type() default MortarRelationType.MANY_TO_ONE;

    /**
     * Local SQL column used for the join.
     *
     * @return local SQL column name
     */
    String localColumn();

    /**
     * Target SQL column used for the join.
     *
     * @return target SQL column name
     */
    String targetColumn() default "id";

    /**
     * Whether the relationship may be absent.
     *
     * @return true when the relationship is nullable
     */
    boolean nullable() default true;
}
