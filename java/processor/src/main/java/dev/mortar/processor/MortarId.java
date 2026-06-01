package dev.mortar.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the identifier column used by generated primary-key lookup executors.
 */
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD })
@Retention(RetentionPolicy.SOURCE)
public @interface MortarId {
}
