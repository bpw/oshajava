package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

//@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Groups {

	/**
	 * A list of communication groups to declare.
	 */
	Group[] communication();

    /**
     * A list of communication groups to declare.
     */
    InterfaceGroup[] intfc();

}
