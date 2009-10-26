package oshajava.instrument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.util.CheckClassAdapter;

import oshajava.runtime.RuntimeMonitor;

import acme.util.Util;

/**
 * TODO options
 * 
 * + dump instrumented class files? where?
 * 
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
 * TODO OshaJavaMain for installing shutdown hook to dump recorded graph in inference mode.
 * 
 * TODO in premain, set the system class loader to be an ICL.
 * 
 * @author benw
 *
 */
public class InstrumentationAgent implements ClassFileTransformer {

	protected static final String DEBUG_KEY = "instrument";

	static class Options {	
		public boolean debug = true;
		public boolean verifyInput = true;
		public String  bytecodeDump = "oshajdump";
		public boolean java6 = false;
		public boolean instrument = true;
		public boolean coarseArrayStates = true;
		public boolean coarseFieldStates = false;
		public boolean instrumentFinalFields = false;

		public boolean verifyOutput() {
			return debug;
		}
		public boolean frames() {
			return java6;
		}
	}

	public final Options opts;

	public InstrumentationAgent(Options opts) {
		this.opts = opts;
	}

	public byte[] instrument(String className, byte[] bytecode) {
		if (ClassInstrumentor.shouldInstrument(className)) {
			final ClassReader in = new ClassReader(bytecode);
			final ClassWriter out = new ClassWriter(in, opts.frames() ? ClassWriter.COMPUTE_FRAMES : 0);
			ClassVisitor chain = out;
			// Build a chain according to the options.
			if (opts.verifyOutput()) {
				chain = new CheckClassAdapter(chain);
			}
			chain = new ClassInstrumentor(chain, opts);
			if (!opts.java6) {
				chain = new RemoveJava6Adapter(chain);
			}
			Remapper r = new SimpleRemapper(toCopy);
			chain = new RemappingClassAdapter(chain, r);

			if (opts.verifyInput) {
				chain = new CheckClassAdapter(chain);
			}
			Util.debugf(DEBUG_KEY, "Instrumenting %s", className);
			in.accept(chain, ClassReader.SKIP_FRAMES);
			return out.toByteArray();
		} else {
			Util.debugf(DEBUG_KEY, "Ignored %s", className);
			return bytecode;
		}

	}

	/*********************************************************************************************/

	//	private static InstrumentationAgent agent;
	protected static final HashMap<String,String> toCopy = new HashMap<String,String>();

	public static void premain(String agentArgs, Instrumentation inst) {
		try {
			// TODO if args say to infer/record, Runtime.getRuntime().addShutdownHook(dumper);
			Util.debug(DEBUG_KEY, "Loading oshajava runtime");
			// Register the instrumentor with the jvm as a class file transformer.
			InstrumentationAgent agent = new InstrumentationAgent(new Options());

			//			System.setProperty("java.system.class.loader", "oshajava.instrument.InstrumentingClassLoader");
			//			Util.assertTrue(loader instanceof InstrumentingClassLoader);
			// TODO do we miss anything loaded later by asm, acme this way?
			synchronized(toCopy) {
				for (Class<?> c : inst.getAllLoadedClasses()) {
					String name = c.getCanonicalName();
					if (name != null) {
						name = name.replaceAll("\\.", "/");
						if (ClassInstrumentor.shouldInstrument(name)) {
							toCopy.put(name, InstrumentingClassLoader.ALT_JDK_PKG + "/" + name);
						}
					}
				}
			}
			inst.addTransformer(agent);
			Util.debug(DEBUG_KEY, "Starting application");
		} catch (Throwable e) {
			Util.log("Problem installing oshajava instrumentor");
			Util.fail(e);
		}
	}

	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
			ProtectionDomain pd, byte[] bytecode) throws IllegalClassFormatException {
		Util.log(className);
		if (!opts.instrument) return null;
		try {
			final byte[] instrumentedBytecode = instrument(className, bytecode);
			RuntimeMonitor.loadNewMethods();
			if (opts.bytecodeDump != null) {
				File f = new File(opts.bytecodeDump + File.separator + className + ".class");
				f.getParentFile().mkdirs();
				BufferedOutputStream insFile = new BufferedOutputStream(new FileOutputStream(f));
				insFile.write(instrumentedBytecode);
				insFile.flush();
				insFile.close();
			}
			return instrumentedBytecode;
		} catch (Throwable e) {
			Util.log("Problem running oshajava instrumentor");
			Util.fail(e);
			return null;
		}
	}

}
