package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Reader {
	/**
	 * List of IDs of channels from which the annotated method reads.
	 */
	String[] value();
}
