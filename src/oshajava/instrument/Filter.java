package oshajava.instrument;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.HashSet;

import oshajava.spec.names.FieldDescriptor;
import oshajava.spec.names.MethodDescriptor;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Debug;
import oshajava.support.acme.util.StringMatchResult;
import oshajava.support.acme.util.StringMatcher;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.acme.util.option.CommandLineOption.Kind;
import oshajava.support.org.objectweb.asm.ClassReader;
import oshajava.support.org.objectweb.asm.ClassWriter;
import oshajava.support.org.objectweb.asm.commons.Remapper;

public class Filter {
	public static final String DEBUG_KEY = "filter";
	
    // TODO
    public static final CommandLineOption<StringMatcher> instrumentClassesOption =
    	CommandLine.makeStringMatcher("classes", StringMatchResult.ACCEPT, Kind.EXPERIMENTAL, 
    			"Only track memory operations on fields and in methods in matching classes (by fully qualified name).", 
    			"-^oshajava\\..*", "-^java\\..*", "-^com.sun\\..*", "-^sun\\..*", "-.*\\[\\]$");

    // TODO
    public static final CommandLineOption<StringMatcher> instrumentFieldsOption =
    	CommandLine.makeStringMatcher("fields", StringMatchResult.ACCEPT, Kind.EXPERIMENTAL, 
    			"Only track memory operations on matching fields (by fully qulified name).", "-.*this\\$.*");

    // TODO
    public static final CommandLineOption<StringMatcher> instrumentMethodsOption =
    	CommandLine.makeStringMatcher("methods", StringMatchResult.ACCEPT, Kind.EXPERIMENTAL, 
    			"Only track memory operations in matching methods (by fully qulified name).");
    
    public static final CommandLineOption<StringMatcher> remapTypesOption =
    	CommandLine.makeStringMatcher("remap", StringMatchResult.ACCEPT, Kind.EXPERIMENTAL, 
    			"Remap these classes to allow more instrumentation.  Use in concert with -classes." + 
    			"  Only use this if you know what you're doing! Mwahahaha!",
    			"-^oshajava\\..*",
    			"-^java\\..*", "-^java\\.lang\\.Object$", "-^com.sun\\..*", "-^sun\\..*");
    
    
	protected static final HashSet<ObjectTypeDescriptor> untouchedLoadedClasses = new HashSet<ObjectTypeDescriptor>();
	protected static boolean isUninstrumented(ObjectTypeDescriptor classType) {
		synchronized (untouchedLoadedClasses) {
			return untouchedLoadedClasses.contains(classType);
		}
	}
	protected static boolean isUninstrumented(MethodDescriptor method) {
		return isUninstrumented(method.getClassType());
	}
	
	protected static boolean isOrWillBeUninstrumented(ObjectTypeDescriptor classType) {
		return isUninstrumented(classType) || !shouldInstrument(classType);
	}
	protected static boolean isOrWillBeUninstrumented(MethodDescriptor method) {
		return isUninstrumented(method) || !shouldInstrument(method);
	}

	protected static boolean shouldInstrument(ObjectTypeDescriptor className) {
		return instrumentClassesOption.get().test(className.getSourceName()) == StringMatchResult.ACCEPT;
	}
	protected static boolean shouldInstrument(FieldDescriptor field) {
		return shouldInstrument(field.getDeclaringType()) && instrumentFieldsOption.get().test(field.getSourceName()) == StringMatchResult.ACCEPT;
	}
	protected static boolean shouldInstrument(MethodDescriptor method) {
		return shouldInstrument(method.getClassType()) && 
			instrumentMethodsOption.get().test(method.getSourceName()) == StringMatchResult.ACCEPT;
	}
	
	protected static boolean hasUninstrumentedOuterClass(ObjectTypeDescriptor type) {
		Debug.debugf("innerclass", "hasUninstrumentedOuterClasses(%s)", type);
		return type.isInner() && untouchedLoadedClasses.contains(type.getOuterType());
	}
	
	/**
	 * For remapping things like java.util.* to __osha__.java.util.*.
	 */
	protected static final Remapper remapper = new Remapper() {
		@Override
		public String map(String typeName) {
			if (!typeName.startsWith(PREFIX) && remapTypesOption.get().test(typeName) == StringMatchResult.ACCEPT) {
				return PREFIX + typeName;
			}
			return typeName;
		}
	};
	
	public static final String PREFIX = "__$osha$__";
	
	/**
	 * The mapping loader.
	 */
	private static final ClassLoader loader = new ClassLoader(ClassLoader.getSystemClassLoader()) {
		@Override
		protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
			if (name.startsWith(PREFIX)) {
				ClassReader cr;
				try {
					cr = new ClassReader(getResourceAsStream(name.substring(PREFIX.length())));
				} catch (IOException e) {
					ClassNotFoundException c = new ClassNotFoundException(name);
					c.initCause(e);
					throw c;
				}
				final ClassWriter cw = new ClassWriter(cr, 0);
				cr.accept(cw, 0);
				final byte[] bytecode = cw.toByteArray();
				return defineClass(name, bytecode, 0, bytecode.length);
			} else {
				throw new ClassNotFoundException();
			}
		}
		// FIXME rewrite all calls to ClassLoader.getSystemClassLoader() etc. to be calls to InstrumentingClassLoader.get()...
	};
	
	/**
	 * Get the mapping loader.
	 * @return
	 */
	public static ClassLoader getMappingLoader() {
		return loader;
	}

	protected static boolean isArrayClass(String name) {
		return name.endsWith("[]");
	}
	
	
	/**
	 * Register classes that have been loaded before the instrumentor.
	 * @param preloadedClasses
	 */
	public static void init(Instrumentation inst) {
		Debug.debug(DEBUG_KEY, "Recording previously loaded classes.");
		// TODO do we miss anything loaded later by asm, oshajava.support.acme this way?
		synchronized(untouchedLoadedClasses) {
			Class<?>[] preloadedClasses = inst.getAllLoadedClasses();
			registerUntouchedClasses(preloadedClasses);
			// Just in case that caused more to load:
			int numLoaded = preloadedClasses.length;
			preloadedClasses = inst.getAllLoadedClasses();
			if (preloadedClasses.length > numLoaded) {
				registerUntouchedClasses(preloadedClasses);
			}
		}
	}
	private static void registerUntouchedClasses(Class<?>[] preloadedClasses) {
		for (Class<?> c : preloadedClasses) {
			String name = c.getCanonicalName();
			if (name != null && !isArrayClass(name)) {
				ObjectTypeDescriptor type = TypeDescriptor.ofClass(name);
				untouchedLoadedClasses.add(type);
			}
		}
	}
	protected static void registerUntouchedClass(ObjectTypeDescriptor cls) {
		untouchedLoadedClasses.add(cls);
	}

}
