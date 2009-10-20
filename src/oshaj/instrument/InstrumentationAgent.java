package oshaj.instrument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

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

	public static void premain(String agentArgs, Instrumentation inst) {
		try {
			Util.log("Loading oshaj");
			// Register the instrumentor with the jvm as a class file transformer.
			inst.addTransformer(new InstrumentationAgent());
			Util.log("Starting application");
		} catch (Throwable e) {
			Util.log("Problem installing oshaj class transformer.");
			Util.fail(e);
		}
	}


	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain pd, byte[] classFileBuffer) throws IllegalClassFormatException {
		try {
			if (ClassInstrumentor.shouldInstrument(className)) {
				// Use ASM to insert hooks for all the sorts of accesses, acquire, release, maybe fork, join, volatiles, etc.
				final ClassReader cr = new ClassReader(classFileBuffer);
				final ClassWriter cw = new ClassWriter(cr, 0); //ClassWriter.COMPUTE_FRAMES); 
				// TODO figure out how to do frames manually. COMPUTE_MAXS is a 2x cost!
				// For now we just drop them and change the version to 5 (no other
				// differences with 6...)
				Util.log("Instrumenting " + className);
				cr.accept(new ClassInstrumentor(new CheckClassAdapter(cw)), ClassReader.SKIP_FRAMES);
				classFileBuffer = cw.toByteArray();
				File f = new File("oshajdump/" + className + ".class");
				f.getParentFile().mkdirs();
				BufferedOutputStream insFile = new BufferedOutputStream(new FileOutputStream(f));
				insFile.write(classFileBuffer);
				insFile.flush();
				insFile.close();
				return classFileBuffer;
			} else {
				Util.log("Ignored " + className);
				return classFileBuffer;
			}
		} catch (Throwable e) {
			Util.log("Problem running oshaj class transformer.");
			Util.fail(e);
		}
		return classFileBuffer;
	}

}
