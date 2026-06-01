package dev.mortar.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java field or record component as a generated SQL column.
 */
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD })
@Retention(RetentionPolicy.SOURCE)
public @interface MortarColumn {
    /**
     * SQL column name.
     *
     * @return SQL column name
     */
    String name();

    /**
     * Whether the column may contain SQL null values.
     *
     * @return true when the column is nullable
     */
    boolean nullable() default true;
}
