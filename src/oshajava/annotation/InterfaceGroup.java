package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

//@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface InterfaceGroup {

	/**
	 * The unique ID of this interface group.
	 */
	String id();

}
