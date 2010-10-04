package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Group {

	/**
	 * The unique ID of this group.
	 */
	String id();
	
//	/**
//	 * The module this group belongs to.
//	 */
//	String group() default "";
//	
//	/**
//	 * List of groups this group delegates to.
//	 */
//	String[] delegate() default {};
//	
//	/**
//	 * List of groups this group merge.
//	 */
//	String[] merge() default {};

}
