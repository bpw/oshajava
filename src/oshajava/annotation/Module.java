package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Module {
	/**
	 * Qualified name of the module that this class belongs to.
	 * @return
	 */
	String value();
}
