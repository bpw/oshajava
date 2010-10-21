package oshajava.instrument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import oshajava.runtime.Config;
import oshajava.spec.exceptions.ModuleSpecNotFoundException;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.acme.util.option.CommandLineOption.Kind;
import oshajava.support.acme.util.option.Option;
import oshajava.support.org.objectweb.asm.ClassReader;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.ClassWriter;
import oshajava.support.org.objectweb.asm.util.CheckClassAdapter;
import oshajava.util.count.ConcurrentTimer;

/**
 * 
 * 
 * TODO Write a tool with ASM that finds all dependencies on java.*, copies them, and
 * rewrites the bytecodes to generate and use the copies. Can't work on classes with native methods.
 * This would get us around GPL issues if we every released with BSD/MIT...
 * 
 * TODO pair with OshaJavaMain and InstrumentingClassLoader.
 * If it's an ICL trying to load something from javacopy.*, then it's
 * the application, and it should be instrumented.
 * If it's not, then it's asm or oshajava or oshajava.support.acme loading something
 * and it should not be instrumented.
 * 
 * TODO When instrumenting a class, the java.* types it uses should be rewritten as follows:
 * If their initializing class classLoader was an ICL or if they are not loaded, then do not rewrite.
 * If their initializing class laoder was not an ICL, they were not
 * instrumented, so rewrite them to be javacopy.*.
 * As ICL loads classes, it records the names/types it loaded so this is easy to determine.
 * 
 * TODO When loading a javacopy.* class, open the classes.jar behind the scenes, pull out the
 * corresponding java.* class and rewrite it to be javacopy.* before the restof instrumentation.
 * 
 * TODO in transform(), if classLoader is ICL, do the instrumentation.  If not, don't.  Actually, push this
 * all to the ICL? No, because we don't get to hook the byte array there.
 * 
 * TODO in premain, set the system class classLoader to be an ICL.
 * 
 * @author bpw
 *
 */
public class Agent implements ClassFileTransformer {

	public static final String DEBUG_KEY = "inst";
	protected static final String IGNORED_DEBUG_KEY = "ignored";

	public static final CommandLineOption<Boolean> bytecodeDumpOption =
		CommandLine.makeBoolean("bytecodeDump", false, Kind.STABLE, "Dump instrumented bytecode.");
	
	public static final CommandLineOption<String> bytecodeDumpDirOption =
		CommandLine.makeString("bytecodeDumpDir", 
				Util.outputPathOption.get() + File.separator + "bytecode-dump", Kind.STABLE, "Location of instrumented bytecode dump.");
	
	public static final CommandLineOption<Boolean> verifyOption =
		CommandLine.makeBoolean("verify", false, Kind.STABLE, "Run an extra debugging verification pass on instrumented bytecode before loading.");
	
	public static final CommandLineOption<Boolean> preVerifyOption =
		CommandLine.makeBoolean("verifySanity", false, Kind.STABLE, "Verify classes read from disk before instrumenting.");
	
	public static final CommandLineOption<Boolean> framesOption =
		CommandLine.makeBoolean("frames", false, Kind.EXPERIMENTAL, "Handle frames intelligently.");
	
	public static final CommandLineOption<Boolean> ignoreMissingMethodsOption =
		CommandLine.makeBoolean("ignoreMissingMethods", false, Kind.DEPRECATED, "Ignore and inline methods missing from their modules.  (See -" + 
				Config.noSpecOption.getId() + " and -" + Config.noSpecActionOption.getId() + " instead.)");
	
    // FIXME see RuntimeMonitor.Ref
	public static final CommandLineOption<Boolean> volatileShadowOption =
		CommandLine.makeBoolean("volatileShadows", false, Kind.EXPERIMENTAL, "Make shadow fields volatile");

    public static final CommandLineOption<Boolean> ignoreFinalFieldsOption =
    	CommandLine.makeBoolean("ignoreFinalFields", false, Kind.EXPERIMENTAL, "Turn off tracking for all final fields.");
        
    public static final CommandLineOption<Boolean> remapOption =
    	CommandLine.makeBoolean("remap", false, Kind.EXPERIMENTAL, "Remap uses of preloaded classes to allow them to be instrumented.");
    
   /*****************/
    
    /**
     * A timer for instrumentation.
     */
    private static final ConcurrentTimer insTimer = new ConcurrentTimer("Instrumentation time");
	
	/**
	 * The main class that will be run by this JVM.
	 */
	private static ObjectTypeDescriptor mainClass;
	
	/**
	 * Main class in option form for nice output.
	 */
	private static final Option<String> mainClassOption = new Option<String>("mainClass", "");

	/**
	 * The root thread of the application.  Classes loaded in threads that are not descendants 
	 * of this thread will not be instrumented.
	 */
	private static ThreadGroup appThreadGroupRoot;
	
	/**
	 * Initialize the instrumentor so it knows the main class and the root ThreadGroup
	 * of the program.
	 */
	public static void initializeProgram(String cl, ThreadGroup tg) {
		mainClass = TypeDescriptor.ofClass(cl);
		Debug.debugf(DEBUG_KEY, "Initializing instrumentor for main class %s with root thread %s.", mainClass, tg.getName());
		mainClassOption.set(mainClass.getInternalName());
		appThreadGroupRoot = tg;
	}

	/**
	 * Instrumentation state.  This is turned on when we load the main class and turned
	 * off when the program finishes.
	 */
	private static volatile boolean instrumenting = false;
	
	/**
	 * Turn off instrumentation from now on.
	 */
	public static void stopInstrumentation() {
		Debug.debug(DEBUG_KEY, "Turning off instrumentation");
		instrumenting = false;
	}
	
	/**
	 * Transform a class on load:
	 * - Remap names.
	 * - Instrument.
	 */
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
			ProtectionDomain pd, byte[] bytecode) throws IllegalClassFormatException {
		if (remapOption.get() && !Rewriter.isMapped(className)) {
			Debug.debugf(IGNORED_DEBUG_KEY, "Ignoring %s (raw)", className);
			return null;
		}
		final ObjectTypeDescriptor cls = TypeDescriptor.ofClass(Rewriter.unmap(className));
		
		if (!instrumenting) {
			if(cls.equals(mainClass)) {
				instrumenting = true;
				Debug.debugf(DEBUG_KEY, "Loading main class (%s) and starting instrumentation.", mainClass);
			} else {
				Debug.debugf(IGNORED_DEBUG_KEY, "Ignoring %s (Instrumentation is off.)", className);
				// FIXME add to remapping list.
				return null;
			}
		} else if (!appThreadGroupRoot.parentOf(Thread.currentThread().getThreadGroup())) {
			Debug.debugf(IGNORED_DEBUG_KEY, "Ignoring %s", className);
			// FIXME add to remapping list.
			return null;
		} else if (Filter.isArrayClass(className)) {
			return null;
		} else if (loader == null) {
			loader = classLoader;
//			// FIXME Really skip these?
//			if (Filter.shouldInstrument(cls) && !Filter.shouldRemap(cls)) {
//				Assert.warn("%s was not instrumented and will not be remapped!  [Implementation detail: Consider substituting system class classLoader.]", className);
//			}
//			// FIXME add to remapping list.
//			return null;
		}
		
		// Go ahead and remap or instrument.
		try {
			insTimer.start();
			final ClassWriter out;
			try {
				final ClassReader in = new ClassReader(bytecode);
				out = new ClassWriter(in, framesOption.get() ? ClassWriter.COMPUTE_FRAMES : 0);

				// Build a chain according to the options.  The chain is constructed END to START.
				ClassVisitor chain = out;
				
				// Optionally verify the output of our instrumentor before passing it to the JVM.
				if (verifyOption.get()) {
					chain = new CheckClassAdapter(chain);
				}
				
				// Remap things like java.util.* to __osha__java.util.*
				if (remapOption.get()) {
					chain = new Rewriter(chain);
				}
				
				// If the class matches the instrumentation filter, instrument it.
				if (Filter.shouldInstrument(cls)) { // TODO check.
					chain = new ClassInstrumentor(chain, loader);
				}
				
				// Optionally treat frames correctly instead of downgrading to Java 1.5 bytecodes.
				if (!framesOption.get()) {
					chain = new RemoveJava6Adapter(chain);
				}
				
				// Optionally verify that what comes from disk is well-formed before trying any transformations on it.
				if (preVerifyOption.get()) {
					chain = new CheckClassAdapter(chain);
				}
				
				// Now run it through the chain.
				Debug.debugf(DEBUG_KEY, "Instrumenting %s", className);
				in.accept(chain, ClassReader.SKIP_FRAMES); // TODO implement frames option correctly
			} catch (ModuleSpecNotFoundException.Wrapper e) {
				throw e.unwrap();
			}
			
			// Harvest our freshly instrumented bytecode.
			final byte[] instrumentedBytecode = out.toByteArray();
			
			// Optionally dump the instrumented bytecode for debugging.
			if (instrumentedBytecode != bytecode && bytecodeDumpOption.get()) {
				File f = new File(bytecodeDumpDirOption.get() + File.separator + className + ".class");
				f.getParentFile().mkdirs();
				BufferedOutputStream insFile = new BufferedOutputStream(new FileOutputStream(f));
				insFile.write(instrumentedBytecode);
				insFile.flush();
				insFile.close();
			}
			
			// Feed the hungry JVM.
			return instrumentedBytecode;
		} catch (ModuleSpecNotFoundException e) {
			Assert.fail(e);
			return null;
		} catch (Throwable e) {
			Assert.fail(e);
			return null;
		} finally {
			insTimer.stop();
		}
	}

	/*******************************/
	
	/**
	 * The mapping classLoader.
	 */
	private static final ClassLoader classLoader = new ClassLoader(ClassLoader.getSystemClassLoader()) {
		@Override
		protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
			if (name.startsWith(Rewriter.PREFIX)) {
				ClassReader cr;
				final String actualClass = Rewriter.unmap(name);
				try {
					Debug.debugf("remap", "%s -> %s", name, actualClass);
					cr = new ClassReader(getResourceAsStream(actualClass.replace('.', '/') + ".class"));
				} catch (IOException e) {
					ClassNotFoundException c = new ClassNotFoundException("IOException trying to load class file for" + actualClass, e);
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
	 * Get the mapping classLoader.
	 * @return
	 */
	public static ClassLoader getMappingLoader() {
		return classLoader;
	}


}
