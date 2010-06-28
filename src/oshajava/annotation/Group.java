package oshajava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
public @interface Group {

	/**
	 * The unique ID of this group.
	 */
	String id();
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
