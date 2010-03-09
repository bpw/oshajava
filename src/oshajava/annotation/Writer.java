package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Writer {
	/**
	 * List of IDs of channels to which this method writes.
	 */
	String[] value();
}
