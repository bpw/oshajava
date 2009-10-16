package oshaj.instrument;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import org.objectweb.asm.Opcodes;

import oshaj.Spec;

// TODO make asm Method, GenericAdapter, etc. use copies of java.util stuff.
// TODO repackage asm into oshaj.org.... so that oshaj can be run on apps that
// use asm.

public class Instrumentor extends ClassAdapter {
	
	/* TODO: Options
	 * 
	 * treat unannotated methods as inline or read-by-all
	 * (or in other words --default-annotation)
	 * 
	 * instrument final fields or not
	 * 
	 */
	
	protected static final String SHADOW_FIELD_SUFFIX = "__osha_state$";
	protected static final String STATE_INIT_METHOD_SUFFIX = "__osha_state_init$";
	protected static final String LOCK_STATE_NAME = "__osha_lock_state$";
	
	protected static final String[] NONINSTRUMENTED_PREFIXES = { "oshaj.", "org.objectweb.asm.", "acme." };
	
	protected static final Type STATE_TYPE = Type.getType(oshaj.runtime.State.class);
	protected static final String STATE_TYPE_NAME = STATE_TYPE.getInternalName();
	protected static final Method STATE_CONSTRUCTOR = new Method(
			STATE_TYPE_NAME + ".\"<init>\"", 
			Type.VOID_TYPE, 
			new Type[0]
	);
	
	protected static final Type THREAD_TYPE = Type.getType(Thread.class);
	protected static final Method CURRENT_THREAD_METHOD = new Method(
			"java.lang.Thread.currentThread", 
			THREAD_TYPE, 
			new Type[0]
	);
	protected static final Method GET_TID_METHOD = new Method(
			"java.lang.Thread.getTid", 
			Type.LONG_TYPE, 
			new Type[0]
	);
	
	protected static final Type RUNTIMEMONITOR_TYPE = Type.getType(Spec.class);
	protected static final Method ENTER_HOOK = new Method(
			"oshaj.RuntimeMonitor.enter", 
			Type.VOID_TYPE, 
			new Type[] { Type.INT_TYPE }
	);
	protected static final Method EXIT_HOOK = new Method(
			"oshaj.RuntimeMonitor.exit", 
			Type.VOID_TYPE, 
			new Type[] { Type.INT_TYPE }
	);
		
	protected static final Type[] ACCESS_HOOK_ARGS = { Type.INT_TYPE, STATE_TYPE };
	protected static final Method PRIVATE_READ_HOOK = new Method(
			"oshaj.runtime.Instrumentor.privateRead", 
			Type.VOID_TYPE, 
			ACCESS_HOOK_ARGS
	);
	protected static final Method SHARED_READ_HOOK = new Method(
			"oshaj.runtime.Instrumentor.sharedRead", 
			Type.VOID_TYPE, 
			ACCESS_HOOK_ARGS
	);
	protected static final Method PRIVATE_WRITE_HOOK = new Method(
			"oshaj.runtime.Instrumentor.privateWrite", 
			Type.VOID_TYPE, 
			ACCESS_HOOK_ARGS
	);
	protected static final Method SHARED_WRITE_HOOK = new Method(
			"oshaj.runtime.Instrumentor.sharedWrite", 
			Type.VOID_TYPE, 
			ACCESS_HOOK_ARGS
	);
	
	protected static final Type[] FIRST_WRITE_HOOK_ARGS = { Type.INT_TYPE };
	protected static final Method PRIVATE_FIRST_WRITE_HOOK = new Method(
			"oshaj.runtime.Instrumentor.privateFirstWrite", 
			Instrumentor.STATE_TYPE, 
			FIRST_WRITE_HOOK_ARGS
	);
	protected static final Method SHARED_FIRST_WRITE_HOOK = new Method(
			"oshaj.runtime.Instrumentor.sharedFirstWrite", 
			Instrumentor.STATE_TYPE, 
			FIRST_WRITE_HOOK_ARGS
	);
	
	public static void premain(String agentArgs, Instrumentation inst) {
//		System.err.println("Loaded classes:");
//		java 6+ only: inst.appendToSystemClassLoaderSearch(new JarFile(agentArgs));
//		for (Class<?> c : inst.getAllLoadedClasses()) {
//			System.err.println(c.getName());
//		}
//		System.exit(0);
//		if (inst.isRedefineClassesSupported()) {
//			System.err.println("Class redinition supported.");
//		} else {
//			System.err.println("Class redefinition not supported. :-(");
//		}
		// Register the instrumentor with the jvm as a class file transformer.
		inst.addTransformer(new ClassFileTransformer() {
			public byte[] transform(ClassLoader loader, String className, Class<?> targetClass,
					ProtectionDomain protectionDomain, byte[] classFileBuffer)
					throws IllegalClassFormatException {
				if (shouldInstrument(className)) {
					// Use ASM to insert hooks for all the sorts of accesses, acquire, release, maybe fork, join, volatiles, etc.
					final ClassReader cr = new ClassReader(classFileBuffer);
					final ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES); 
					// TODO figure out how to do frames manually. COMPUTE_MAXS is a 2x cost!
					cr.accept(new Instrumentor(cw, className), 0);
					return cw.toByteArray();
				} else {
					System.err.println("!! Skipped instrumenting " + className);
					return classFileBuffer;
				}
			}			
		});
		// Add the libraries that our instrumentation hooks will call to the classpath.
		// inst.appendToSystemClassLoaderSearch(jarFileForRunTimeHooksEtc);
		System.err.println("oshaj loaded!");
	}
	
	public static boolean shouldInstrument(String className) {
		for (String prefix : NONINSTRUMENTED_PREFIXES) {
			if (className.startsWith(prefix)) return false;
		}
		return true;
	}

	/**************************************************************************/
	
	protected final String className;
	
	public Instrumentor(ClassVisitor cv, String className) {
		super(cv);
		this.className = className;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		// TODO option to ignore final fields.
		// We make all state fields non-final to be able to set them from outside a constructor
		// and volatile to support safe double-checked locking for lazy initialization.
		// TODO this is not optimal, as it enforces more ordering than the application does on its
		// own.
		final FieldVisitor fv = super.visitField(
				access & ~Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE, 
				name + SHADOW_FIELD_SUFFIX, STATE_TYPE_NAME, signature, value
			);
		if (fv != null) {
			fv.visitEnd();
		}
		final String stateFieldName = name + STATE_INIT_METHOD_SUFFIX;
		// Add a method to initialize the shadow field
		// TODO this is currently a safe initializer (synchronized, and the state shadow field is 
		// volatile, to support safe doule-checked locking), but we might be able to apply the
		// "it's OK to have races if the app has races argument" as long as this doesn't cause issues
		// with escaping uninitialized but allocated States.
		// The volatile may be unnecessary if double-checked locking is not an issue if the double-checking
		// is across method boundaries.  This would rely on the JIT not inlining/optimizing across method
		// boundaries... not a safe bet. For now, we play it safe.
		final MethodVisitor mv = super.visitMethod(
				access | Opcodes.ACC_SYNCHRONIZED, stateFieldName, "()V", "", new String[0]);
		final GeneratorAdapter init = new GeneratorAdapter(
				mv, access | Opcodes.ACC_SYNCHRONIZED, name + STATE_INIT_METHOD_SUFFIX, "()V");
		init.visitCode();
		// create a new State. stack -> state
		init.newInstance(STATE_TYPE);
		// dup it. stack -> state state
		init.dup();
		// initialize it. stack -> state
		init.invokeConstructor(STATE_TYPE, STATE_CONSTRUCTOR);
		// dup it. stack -> state state
		init.dup();
		if ((access & Opcodes.ACC_STATIC) != 0) {
			// static field
			// put static. stack -> state
			init.putStatic(Type.getType(className), stateFieldName, STATE_TYPE);
			// return state. stack ->
			init.returnValue();
			// max stack: 2; max locals: 0
			init.visitMaxs(2, 0);
		} else {
			// instance field
			// get this. stack -> state state this
			init.loadThis();
			// put field. stack -> state
			init.putField(Type.getType(className), stateFieldName, STATE_TYPE);
			// return state. stack ->
			init.returnValue();
			// max stack: 3; max locals: 0
			init.visitMaxs(3, 0);
		}
		init.visitEnd();
		
		return super.visitField(access, name, desc, signature, value);
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		final int id = Spec.getId(name, desc, signature);
		return new MethodInstrumentor(super.visitMethod(access, name, desc, signature, exceptions),
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
