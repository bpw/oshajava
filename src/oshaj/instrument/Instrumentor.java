package oshaj.instrument;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import oshaj.*;
import oshaj.runtime.State;

public class Instrumentor extends ClassAdapter {
	
	public static final String SHADOW_FIELD_SUFFIX = "__osha_state$";
	public static final String LOCK_STATE_NAME = "__osha_lock_state$";
	
	public static final Type STATE_TYPE = Type.getType(oshaj.runtime.State.class);
	public static final String STATE_TYPE_NAME = STATE_TYPE.getInternalName();
	public static final Method STATE_CONSTRUCTOR = new Method(STATE_TYPE_NAME + ".\"<init>\"", Type.VOID_TYPE, new Type[0]);
	
	private static final Type[] HOOK_ARGS = { Type.LONG_TYPE, Type.INT_TYPE };
	public static final Method PRIVATE_READ_HOOK = new Method("oshaj.runtime.State.privateRead", Type.VOID_TYPE, HOOK_ARGS);
	public static final Method SHARED_READ_HOOK = new Method("oshaj.runtime.State.sharedRead", Type.VOID_TYPE, HOOK_ARGS);
	public static final Method PRIVATE_WRITE_HOOK = new Method("oshaj.runtime.State.privateWrite", Type.VOID_TYPE, HOOK_ARGS);
	public static final Method SHARED_WRITE_HOOK = new Method("oshaj.runtime.State.sharedWrite", Type.VOID_TYPE, HOOK_ARGS);
	
	public Instrumentor(ClassVisitor cv) {
		super(cv);
	}

	public static void premain(String agentArgs, Instrumentation inst) {
//		System.err.println("Loaded classes:");
//		java 6+ only: inst.appendToSystemClassLoaderSearch(new JarFile(agentArgs));
//		for (Class<?> c : inst.getAllLoadedClasses()) {
//			System.err.println(c.getName());
//		}
//		System.exit(0);
		if (inst.isRedefineClassesSupported()) {
			System.err.println("Class redinition supported.");
		}
		// Register the instrumentor with the jvm as a class file transformer.
		inst.addTransformer(new ClassFileTransformer() {
			public byte[] transform(ClassLoader loader, String className, Class<?> targetClass,
					ProtectionDomain protectionDomain, byte[] classFileBuffer)
					throws IllegalClassFormatException {
				if (className.startsWith("oshaj.")) {
					System.err.println("!! Skipped instrumenting " + className);
					return classFileBuffer;
				} else {
					// Use ASM to insert hooks for all the sorts of accesses, acquire, release, maybe fork, join, volatiles, etc.
					final ClassReader cr = new ClassReader(classFileBuffer);
					final ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
					cr.accept(new Instrumentor(cw), 0);
					return cw.toByteArray();
				}
			}			
		});
		// Add the libraries that our instrumentation hooks will call to the classpath.
		// inst.appendToSystemClassLoaderSearch(jarFileForRunTimeHooksEtc);
		System.err.println("oshaj loaded!");
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		// TODO We only need to watch non-final fields... maybe.
		// Technically we should record THE write and then all subsequent reads are communication.
		// But we might be able to cheat on that for performance.
//		if ((access & Opcodes.ACC_FINAL) == 0) {
		// if we're accessing the original, then the private, protected, public whatever will work out.
		final FieldVisitor fv = super.visitField(access, name + SHADOW_FIELD_SUFFIX, STATE_TYPE_NAME, signature, value);
		if (fv != null) {
			fv.visitEnd();
		}
//		}
		// TODO Don't forget to initialize all those shadow fields... statics different than instance.
		return super.visitField(access, name, desc, signature, value);
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		final int id = Spec.getId(name, desc, signature);
		return new MethodInstrumentor(cv.visitMethod(access, name, desc, signature, exceptions), 
				access, name, desc,
				id, Spec.inlined(id), Spec.inEdges(id), Spec.outEdges(id));
	}
	
//	@Override
//	public void visitEnd() {
//		// insert field to hold lock State.
//		// nice to make this final, but it's easier to init it outside the constructor for now,
//		// to avoid making sure I've got exactly one initialization in each...
//		// TODO how to get a lock state field in every Object?
//		super.visitField(Opcodes.ACC_PUBLIC, LOCK_STATE_NAME, STATE_TYPE.getDescriptor(), null, null);
//		super.visitEnd();
//	}

}
