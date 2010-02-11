package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
public @interface Member {
	/**
	 * Qualified name of the module that this class belongs to.
	 * @return
	 */
	String value();
}
