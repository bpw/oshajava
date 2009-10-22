package oshaj.instrument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

import oshaj.runtime.RuntimeMonitor;

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
 * rewrites the bytecodes to use the copies. Can't work on classes with native methods.
 * 
 * @author benw
 *
 */
public class InstrumentationAgent implements ClassFileTransformer {
	
	protected static final String DEBUG_KEY = "instrument";
	
	static class Options {	
		public boolean debug = false;
		public boolean verifyInput = true;
		public String  bytecodeDump = "oshajdump";
		public boolean java6 = false;
		public boolean instrument = true;
		public boolean coarseArrayStates = true;
		public boolean coarseFieldStates = false;
		
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
	
	public static void premain(String agentArgs, Instrumentation inst) {
		try {
			Util.debug(DEBUG_KEY, "Loading oshaj");
			// Register the instrumentor with the jvm as a class file transformer.
			inst.addTransformer(new InstrumentationAgent(new Options()));
			Util.debug(DEBUG_KEY, "Starting application");
		} catch (Throwable e) {
			Util.log("Problem installing oshaj class transformer.");
			Util.fail(e);
		}
	}
	
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
			ProtectionDomain pd, byte[] bytecode) throws IllegalClassFormatException {
		if (!opts.instrument) return bytecode;
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
			Util.log("Problem running oshaj class transformer.");
			Util.fail(e);
			return bytecode;
		}
	}

}
