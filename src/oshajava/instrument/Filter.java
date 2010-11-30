package oshajava.instrument;

import oshajava.spec.names.FieldDescriptor;
import oshajava.spec.names.MethodDescriptor;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.StringMatchResult;
import oshajava.support.acme.util.StringMatcher;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.acme.util.option.CommandLineOption.Kind;

public class Filter {
	public static final String DEBUG_KEY = "filter";
	
    public static final CommandLineOption<StringMatcher> instrumentClassesOption =
    	CommandLine.makeStringMatcher("classes", StringMatchResult.ACCEPT, Kind.STABLE, 
    			"Only track memory operations on fields and in methods in matching classes (by fully qualified name).", 
    			"-^oshajava\\..*", "-^java\\..*", "-^com.sun\\..*", "-^sun\\..*");

    public static final CommandLineOption<StringMatcher> instrumentFieldsOption =
    	CommandLine.makeStringMatcher("fields", StringMatchResult.ACCEPT, Kind.STABLE, 
    			"Only track memory operations on matching fields (by fully qulified name).", "-.*this\\$.*");

    public static final CommandLineOption<StringMatcher> instrumentMethodsOption =
    	CommandLine.makeStringMatcher("methods", StringMatchResult.ACCEPT, Kind.STABLE, 
    			"Only track memory operations in matching methods (by fully qulified name).");
    
	/**
     * Classes that are never allowed to be instrumented or remapped.
     */
    private static final StringMatcher blacklist = new StringMatcher(StringMatchResult.ACCEPT,
    		"-^java\\.lang\\.Object$");
    
    /**
     * Check if a class is an array class.
     * @param name
     * @return
     */
    protected static boolean isArrayClass(String name) {
		return name.endsWith("[]");
	}
	
	/**
	 * Should the given class be instrumented?
	 * @param className
	 * @return
	 */
	protected static boolean shouldInstrument(ObjectTypeDescriptor className) {
		if (blacklist.test(className.getSourceName()) != StringMatchResult.REJECT && 
			instrumentClassesOption.get().test(className.getSourceName()) == StringMatchResult.ACCEPT) {
			if (className.isInner() && !shouldInstrument(className.getOuterType())) {
				Assert.warn("Would instrument class %s, but its outer class %s is uninstrumented.", className, className.getOuterType());
				return false;
			} else {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Should the given field be instrumented?
	 * @param field
	 * @return
	 */
	protected static boolean shouldInstrument(FieldDescriptor field) {
		return shouldInstrument(field.getDeclaringType()) && instrumentFieldsOption.get().test(field.getSourceName()) == StringMatchResult.ACCEPT;
	}
	
	/**
	 * Should the given method be instrumented?
	 * @param method
	 * @return
	 */
	protected static boolean shouldInstrument(MethodDescriptor method) {
		return shouldInstrument(method.getClassType()) && 
			instrumentMethodsOption.get().test(method.getSourceName()) == StringMatchResult.ACCEPT;
	}
	
}