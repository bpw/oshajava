package oshajava.instrument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;

import oshajava.runtime.RuntimeMonitor;
import oshajava.sourceinfo.ModuleSpecNotFoundException;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.support.org.objectweb.asm.ClassReader;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.ClassWriter;
import oshajava.support.org.objectweb.asm.commons.RemappingClassAdapter;
import oshajava.support.org.objectweb.asm.commons.SimpleRemapper;
import oshajava.support.org.objectweb.asm.util.CheckClassAdapter;

/**
 * TODO options
 * + dump instrumented class files? where?
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

	protected static final String DEBUG_KEY = "instrument";
	
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
		"java/util/Vector"
	};
	
	static class Options {	
		public boolean debug = true;
		public boolean verifyInput = true;
		public String  bytecodeDump = "oshajava-instrumentation-dump";
		public boolean java6 = false;
		public boolean instrument = true;
		public boolean coarseArrayStates = true;
		public boolean coarseFieldStates = false;
		public boolean instrumentFinalFields = false;
		public boolean remapJDK = false;
		public String methodTableFile = null;

		public boolean verifyOutput() {
			return debug;
		}
		public boolean frames() {
			return java6;
		}
	}

	public final Options opts;
	
	protected final Spec spec = new Spec();

	public InstrumentationAgent(Options opts) {
		this.opts = opts;
//		methodTable = opts.methodTableFile == null ? new MethodTable() : (MethodTable)ColdStorage.load(opts.methodTableFile);
	}

	public byte[] instrument(String className, byte[] bytecode) throws ModuleSpecNotFoundException {
		final ClassReader in = new ClassReader(bytecode);
		final ClassWriter out = new ClassWriter(in, opts.frames() ? ClassWriter.COMPUTE_FRAMES : 0);
		ClassVisitor chain = out;
		// Build a chain according to the options.
		if (opts.verifyOutput()) {
			chain = new CheckClassAdapter(chain);
		}
		chain = new ClassInstrumentor(chain, opts, spec);
		if (!opts.java6) {
			chain = new RemoveJava6Adapter(chain);
		}
		if (opts.remapJDK) {
			chain = new RemappingClassAdapter(chain, new SimpleRemapper(uninstrumentedLoadedClasses));
		}
		if (opts.verifyInput) {
			chain = new CheckClassAdapter(chain);
		}
		Util.logf("Instrumenting %s", className);
		in.accept(chain, ClassReader.SKIP_FRAMES);
		return out.toByteArray();
	}

	/*********************************************************************************************/

	protected static final HashMap<String,String> uninstrumentedLoadedClasses = new HashMap<String,String>();

	public static void premain(String agentArgs, Instrumentation inst) {
		try {
			Thread.currentThread().setName("oshajava");
			// TODO if args say to infer/record, Runtime.getRuntime().addShutdownHook(dumper);
			Util.log("Loading oshajava runtime");
			// Register the instrumentor with the jvm as a class file transformer.
			InstrumentationAgent agent = new InstrumentationAgent(new Options());

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
		mainClassInternalName = cl.replace('.', '/');
	}
	public static void stopInstrumentation() {
		Util.log("Turning off instrumentation");
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
			final byte[] instrumentedBytecode = instrument(className, bytecode);
			RuntimeMonitor.loadNewMethods();
			if (opts.bytecodeDump != null && instrumentedBytecode != bytecode) {
				File f = new File(opts.bytecodeDump + File.separator + className + ".class");
				f.getParentFile().mkdirs();
				BufferedOutputStream insFile = new BufferedOutputStream(new FileOutputStream(f));
				insFile.write(instrumentedBytecode);
				insFile.flush();
				insFile.close();
			}
			return instrumentedBytecode;
		} catch (ModuleSpecNotFoundException e) {
			throw e;
		} catch (Throwable e) {
			Util.log("Problem running oshajava instrumentor");
			Util.fail(e);
			return null;
		}
	}
	
	private boolean shouldTransform(String className) {
		if (opts.instrument) {
			if (!instrumentationOn) {
				if(className.equals(mainClassInternalName)) {
					instrumentationOn = true;
					Util.logf("Loading main class (%s) and starting instrumentation.", mainClassInternalName);
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
				Util.logf("Ignoring %s", className);
			}
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
	
//	protected byte[] transform

}
