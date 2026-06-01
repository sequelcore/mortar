package dev.mortar.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD })
@Retention(RetentionPolicy.SOURCE)
public @interface MortarRelation {
    Class<?> target();

    MortarRelationType type() default MortarRelationType.MANY_TO_ONE;

    String localColumn();

    String targetColumn() default "id";

    boolean nullable() default true;
}
