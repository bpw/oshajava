package oshajava.instrument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;

import oshajava.runtime.Config;
import oshajava.sourceinfo.ModuleSpecNotFoundException;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.org.objectweb.asm.ClassReader;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.ClassWriter;
import oshajava.support.org.objectweb.asm.commons.RemappingClassAdapter;
import oshajava.support.org.objectweb.asm.commons.SimpleRemapper;
import oshajava.support.org.objectweb.asm.util.CheckClassAdapter;

/**
 * TODO options
 * + on illegal communication: throw, log, both?
 * 
 * 
 * TODO Write a tool with ASM that finds all dependencies on java.*, copies them, and
 * rewrites the bytecodes to generate and use the copies. Can't work on classes with native methods.
 * This would get us around GPL issues if we every released with BSD/MIT...
 * 
 * TODO pair with OshaJavaMain and InstrumentingClassLoader.
 * If it's an ICL trying to load something from javacopy.*, then it's
 * the application, and it should be instrumented.
 * If it's not, then it's asm or oshajava or acme loading something
 * and it should not be instrumented.
 * 
 * TODO When instrumenting a class, the java.* types it uses should be rewritten as follows:
 * If their initializing class loader was an ICL or if they are not loaded, then do not rewrite.
 * If their initializing class laoder was not an ICL, they were not
 * instrumented, so rewrite them to be javacopy.*.
 * As ICL loads classes, it records the names/types it loaded so this is easy to determine.
 * 
 * TODO When loading a javacopy.* class, open the classes.jar behind the scenes, pull out the
 * corresponding java.* class and rewrite it to be javacopy.* before the restof instrumentation.
 * 
 * TODO in transform(), if loader is ICL, do the instrumentation.  If not, don't.  Actually, push this
 * all to the ICL? No, because we don't get to hook the byte array there.
 * 
 * TODO in premain, set the system class loader to be an ICL.
 * 
 * @author bpw
 *
 */
public class InstrumentationAgent implements ClassFileTransformer {

	protected static final String DEBUG_KEY = "inst";

	protected static final String[] EXCLUDE_PREFIXES = { 
		"oshajava/", 
		"java/lang/", 
		"java/security",
		// TODO
		"java/util",
		"sun/",
		"java/io/",
		"java/util/AbstractCollection",
		"java/util/AbtractMap",
		"java/util/AbstractSet",
		"java/util/ArrayList",
		"java/util/Arrays",
		"java/util/Collections",
		"java/util/concurrent/ConcurrentHashMap",
		"java/util/concurrent/locks/ReentrantLock",
		"java/util/HashMap",
		"java/util/HashSet",
		"java/util/LinkedHashMap",
		"java/util/Vector",
		"java/awt/RenderingHints",
	};

	public static final CommandLineOption<Boolean> fullJDKInstrumentationOption =
		CommandLine.makeBoolean("instrumentFullJDK", false, "Instrument deep into the JDK.");
	
	public static final CommandLineOption<Boolean> bytecodeDumpOption =
		CommandLine.makeBoolean("bytecodeDump", false, "Dump instrumented bytecode.");
	
	public static final CommandLineOption<String> bytecodeDumpDirOption =
		CommandLine.makeString("bytecodeDumpDir", 
				Util.outputPathOption.get() + File.separator + "bytecode-dump", "Location of instrumented bytecode dump.");
	
	public static final CommandLineOption<Boolean> verifyOption =
		CommandLine.makeBoolean("verify", true, "Run an extra debugging verification pass on instrumented bytecode before loading.");
	
	public static final CommandLineOption<Boolean> preVerifyOption =
		CommandLine.makeBoolean("preVerify", true, "Verify classes read from disk before instrumenting.");
	
	public static final CommandLineOption<Boolean> framesOption =
		CommandLine.makeBoolean("frames", false, "Handle frames intelligently.");
		
	public byte[] instrument(String className, byte[] bytecode, ClassLoader loader) throws ModuleSpecNotFoundException {
		try {
			final ClassReader in = new ClassReader(bytecode);
			final ClassWriter out = new ClassWriter(in, framesOption.get() ? ClassWriter.COMPUTE_FRAMES : 0);
			ClassVisitor chain = out;
			// Build a chain according to the options.
			if (verifyOption.get()) {
				chain = new CheckClassAdapter(chain);
			}
			chain = new ClassInstrumentor(chain, loader);
			if (!framesOption.get()) {
				chain = new RemoveJava6Adapter(chain);
			}
			if (fullJDKInstrumentationOption.get()) {
				chain = new RemappingClassAdapter(chain, new SimpleRemapper(uninstrumentedLoadedClasses));
			}
			if (preVerifyOption.get()) {
				chain = new CheckClassAdapter(chain);
			}
			Util.debugf(DEBUG_KEY, "Instrumenting %s", className);
			in.accept(chain, ClassReader.SKIP_FRAMES); // FIXME frames
			return out.toByteArray();
		} catch (ModuleSpecNotFoundException.Wrapper e) {
			throw e.unwrap();
		}
	}

	/*********************************************************************************************/

	protected static final HashMap<String,String> uninstrumentedLoadedClasses = new HashMap<String,String>();

	public static void install(Instrumentation inst) {
		try {
			Util.debug(DEBUG_KEY, "Installing instrumentation agent.");
			// Register the instrumentor with the jvm as a class file transformer.
			InstrumentationAgent agent = new InstrumentationAgent();

			Util.debug(DEBUG_KEY, "Recording previously loaded classes.");
			// TODO do we miss anything loaded later by asm, acme this way?
			synchronized(uninstrumentedLoadedClasses) {
				for (Class<?> c : inst.getAllLoadedClasses()) {
					String name = c.getCanonicalName();
					if (name != null) {
						name = name.replace('.', '/');
						if (shouldInstrument(name)) {
							uninstrumentedLoadedClasses.put(name, InstrumentingClassLoader.ALT_JDK_PKG + "/" + name);
						}
					}
				}
			}
			inst.addTransformer(agent);
		} catch (Throwable e) {
			Util.log("Problem installing oshajava instrumentor");
			Util.fail(e);
		}
	}

	private static volatile boolean instrumentationOn = false;
	private static String mainClassInternalName;
	public static void setMainClass(String cl) {
		Util.debugf(DEBUG_KEY, "Setting main application class to %s.", cl);
		mainClassInternalName = cl.replace('.', '/');
	}
	public static void stopInstrumentation() {
		Util.debug(DEBUG_KEY, "Turning off instrumentation");
		instrumentationOn = false;
	}
	private static ThreadGroup appThreadGroupRoot;
	public static void setAppThreadGroupRoot(ThreadGroup tg) {
		appThreadGroupRoot = tg;
	}

	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
			ProtectionDomain pd, byte[] bytecode) throws IllegalClassFormatException {
		try {
			if (!shouldTransform(className)) return null;
			final byte[] instrumentedBytecode = instrument(className, bytecode, loader);
			//			RuntimeMonitor.loadNewMethods();
			if (instrumentedBytecode != bytecode && bytecodeDumpOption.get()) {
				File f = new File(bytecodeDumpDirOption.get() + File.separator + className + ".class");
				f.getParentFile().mkdirs();
				BufferedOutputStream insFile = new BufferedOutputStream(new FileOutputStream(f));
				insFile.write(instrumentedBytecode);
				insFile.flush();
				insFile.close();
			}
			return instrumentedBytecode;
		} catch (ModuleSpecNotFoundException e) {
			Util.fail(e);
			return null;
		} catch (Throwable e) {
			Util.fail("Problem running oshajava instrumentor: " + e);
			return null;
		}
	}

	private boolean shouldTransform(String className) {
		if (!instrumentationOn) {
			if(className.equals(mainClassInternalName)) {
				instrumentationOn = true;
				Util.debugf(DEBUG_KEY, "Loading main class (%s) and starting instrumentation.", mainClassInternalName);
				return true;
			}
			//				Util.logf("Ignoring %s (Instrumentation not started yet.)", className);
		} else if (appThreadGroupRoot.parentOf(Thread.currentThread().getThreadGroup())
				&& shouldInstrument(className)
				&& !hasUninstrumentedOuterClass(className)) {
			// if this is a thread spawned by the app and we don't ignore this class.
			// TODO this loses finalize methods, called by GC, probably not in an app thread.
			return true;
		} else {
			Util.debugf(DEBUG_KEY, "Ignoring %s", className);
		}
		return false;
	}

	protected static boolean shouldInstrument(String className) {
		for (String prefix : EXCLUDE_PREFIXES) {
			if (className.startsWith(prefix)) return false;
		}
		return true;
	}

	private static boolean hasUninstrumentedOuterClass(String className) {
		final int i = className.lastIndexOf('$');
		if (i == -1) {
			return false;
		} else {
			final String cn = className.substring(0, i);
			return uninstrumentedLoadedClasses.containsKey(cn) || hasUninstrumentedOuterClass(cn);
		}
	}
	
	// -- Utilities for instrumentation --------------
	
	public static String internalName(String sourceName) {
		return sourceName.replace('.', '/');
	}
	public static String sourceName(String internalName) {
		return internalName.replace('/', '.');
	}
	public static String makeFieldSourceName(String className, String fieldName) {
		return sourceName(className) + '.' + fieldName;
	}

}
