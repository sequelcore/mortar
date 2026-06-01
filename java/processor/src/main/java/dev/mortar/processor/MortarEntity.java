package dev.mortar.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java type as an input for Mortar metamodel generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MortarEntity {
    /**
     * SQL table name.
     *
     * @return SQL table name
     */
    String table();

    /**
     * SQL table alias used by generated queries.
     *
     * @return SQL table alias
     */
    String alias() default "";
}
