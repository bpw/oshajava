package oshaj.instrument;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;


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
	
	protected static final String[] NONINSTRUMENTED_PREFIXES = { "oshaj.", "org.objectweb.asm.", "acme." };
	
	protected static final Type STRING_TYPE          = Type.getType(java.lang.String.class);
	protected static final Type INTSET_TYPE          = Type.getType(oshaj.util.IntSet.class);
	protected static final Type BITVECTORINTSET_TYPE = Type.getType(oshaj.util.BitVectorIntSet.class);
	protected static final Type STATE_TYPE           = Type.getType(oshaj.runtime.State.class);
	protected static final Type RUNTIME_MONITOR_TYPE = Type.getType(oshaj.runtime.RuntimeMonitor.class);

	protected static final String STATE_TYPE_NAME = STATE_TYPE.getInternalName();
	protected static final String SHADOW_FIELD_SUFFIX = "__osha_state$";
	protected static final String STATE_INIT_METHOD_SUFFIX = "__osha_state_init$";
	protected static final String LOCK_STATE_NAME = "__osha_lock_state$";
	protected static final String VOID_DESC = "()V";
	protected static final String READERSET_FIELD_PREFIX = "__osha_readers_for_method";
	protected static final String READERSET_INIT_NAME = "__osha_readersets_init$";
	protected static final String READERSET_INIT_DESC = Type.getDescriptor(oshaj.util.IntSet.class);
	
	protected static final int READERSET_INIT_ACCESS = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
	
	protected static final Type[] ARGS_NONE = new Type[0];
	protected static final Type[] ARGS_INT = { Type.INT_TYPE };
	protected static final Type[] ARGS_INT_STATE = { Type.INT_TYPE, STATE_TYPE };
	
	protected static final Method STATE_CONSTRUCTOR = new Method("\"<init>\"", VOID_DESC);

	protected static final Method HOOK_BUILD_SET = 
		new Method("buildSet", BITVECTORINTSET_TYPE, new Type[] {Type.getType(String[].class)});
	
	protected static final Method HOOK_ENTER = new Method("enter", Type.VOID_TYPE, ARGS_INT);
	protected static final Method HOOK_EXIT  = new Method("exit",  Type.VOID_TYPE, ARGS_INT);
	
	protected static final Method HOOK_PRIVATE_READ   = new Method("privateRead", Type.VOID_TYPE, ARGS_INT_STATE);
	protected static final Method HOOK_PROTECTED_READ = new Method("protectedRead", Type.VOID_TYPE, ARGS_INT_STATE);

	protected static final Method HOOK_PRIVATE_WRITE  = new Method("privateWrite", Type.VOID_TYPE, ARGS_INT_STATE);
	protected static final Method HOOK_PRIVATE_FIRST_WRITE   = new Method("privateFirstWrite", STATE_TYPE, ARGS_INT);
	protected static final Method HOOK_PROTECTED_WRITE   = 
		new Method("protectedWrite", Type.VOID_TYPE, new Type[] { Type.INT_TYPE, STATE_TYPE, INTSET_TYPE });
	protected static final Method HOOK_PROTECTED_FIRST_WRITE = 
		new Method("protectedFirstWrite", STATE_TYPE, new Type[] { Type.INT_TYPE, INTSET_TYPE });
	protected static final Method HOOK_PUBLIC_WRITE       = new Method("publicWrite", STATE_TYPE, ARGS_INT_STATE);
	protected static final Method HOOK_PUBLIC_FIRST_WRITE = new Method("publicFirstWrite", STATE_TYPE, ARGS_INT);
	

	
	/****************************************************************************/

	public static void premain(String agentArgs, Instrumentation inst) {
		try {
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
		} catch (Exception e) {
			System.err.println("Problem installing oshaj class transformer.");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public static boolean shouldInstrument(String className) {
		for (String prefix : NONINSTRUMENTED_PREFIXES) {
			if (className.startsWith(prefix)) return false;
		}
		return true;
	}

	/**************************************************************************/
	
	protected final String className;
	protected boolean clinitSeen = false;
	
	// FIXME import
	protected final HashMap<Integer,String[]> readerSets = new HashMap<Integer,String[]>();
	
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
		final String stateFieldName = name + SHADOW_FIELD_SUFFIX;
		final String stateInitMethodName = name + STATE_INIT_METHOD_SUFFIX;
		// Add a method to initialize the shadow field
		// TODO this is currently a safe initializer (synchronized, and the state shadow field is 
		// volatile, to support safe doule-checked locking), but we might be able to apply the
		// "it's OK to have races if the app has races argument" as long as this doesn't cause issues
		// with escaping uninitialized but allocated States.
		// The volatile may be unnecessary if double-checked locking is not an issue if the double-checking
		// is across method boundaries.  This would rely on the JIT not inlining/optimizing across method
		// boundaries... not a safe bet. For now, we play it safe.
		final MethodVisitor mv = super.visitMethod(
				access | Opcodes.ACC_SYNCHRONIZED, stateInitMethodName, VOID_DESC, null, null);
		final GeneratorAdapter init = new GeneratorAdapter(
				mv, access | Opcodes.ACC_SYNCHRONIZED, stateInitMethodName, VOID_DESC);
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
		final int id = MethodRegistry.register(signature);
		return new MethodInstrumentor(super.visitMethod(access, name, desc, signature, exceptions),
				access, name, desc, id, this);
	}
	
	protected void addReaderSet(int mid, String[] readers) {
		readerSets.put(mid, readers);
	}
	
	@Override
	public void visitEnd() {
		if (!clinitSeen) {
			System.err.println("!!!!!  no <clinit> seen. creating one. !!!!!");
			// TODO create clinit method.
			// Create a big ol' static method to initialize them. (This is called from clinit.)
			final MethodVisitor mv = super.visitMethod(
					Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", VOID_DESC, null, null);
			final GeneratorAdapter clinit = new GeneratorAdapter(
					mv, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", VOID_DESC);
			clinit.visitCode();
			clinit.invokeStatic(RUNTIME_MONITOR_TYPE, new Method(READERSET_INIT_NAME, READERSET_INIT_DESC));
			clinit.visitEnd();
		}
		readersetSetup();
		super.visitEnd();
	}
	
	private void readersetSetup() {
		
		// Create all the readerset fields, one per method.
		for (Map.Entry<Integer, String[]> e : readerSets.entrySet()) {
			if (e.getValue() != null) { // @ReadBy needs a field.
				super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, 
						READERSET_FIELD_PREFIX + e.getKey(), VOID_DESC, null, null);
			} 
		}
		
		// Create a big ol' static method to initialize them. (This is called from clinit.)
		final MethodVisitor mv = super.visitMethod(
				READERSET_INIT_ACCESS, READERSET_INIT_NAME, VOID_DESC, null, null);
		final GeneratorAdapter init = new GeneratorAdapter(
				mv, READERSET_INIT_ACCESS, READERSET_INIT_NAME, VOID_DESC);
		init.visitCode();
		
		// initialize each one.
		for (Map.Entry<Integer, String[]> e : readerSets.entrySet()) {
			final String[] readers = e.getValue();
			if (readers != null) { // @ReadBy
				// create an array. stack -> array
				init.newArray(STRING_TYPE);
				// for each index, put in the string.
				for (int i = 0; i < readers.length; i++) {
					// dup. stack -> array array
					init.dup();
					// push index. stack -> array array i
					init.push(i);
					// push string. stack -> array array i string
					init.push(readers[i]);
					// array store. stack -> array
					init.arrayStore(STRING_TYPE);
				}
				// call MethodRegistry.buildSet(array). stack -> set
				init.invokeStatic(RUNTIME_MONITOR_TYPE, HOOK_BUILD_SET);
				// putstatic into shadow field. stack ->
				init.putStatic(Type.getObjectType(className), READERSET_FIELD_PREFIX + e.getKey(), INTSET_TYPE);
			}
		}
		init.visitEnd();
	}

}
