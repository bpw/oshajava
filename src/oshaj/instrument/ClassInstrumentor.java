package oshaj.instrument;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;


// TODO make asm Method, GenericAdapter, etc. use copies of java.util stuff.
// TODO repackage asm into oshaj.org.... so that oshaj can be run on apps that
// use asm.

public class ClassInstrumentor extends ClassAdapter {

	/* TODO: Options
	 * 
	 * treat unannotated methods as inline or read-by-all
	 * (or in other words --default-annotation)
	 * 
	 * instrument final fields or not
	 * 
	 */

	protected static final String[] NONINSTRUMENTED_PREFIXES = { "oshaj/", "org/objectweb/asm/", "acme/", "sun/", "java/" };
	
	protected static final Type STRING_TYPE          = Type.getType(java.lang.String.class);
	protected static final Type STATE_TYPE           = Type.getType(oshaj.runtime.State.class);
	protected static final Type RUNTIME_MONITOR_TYPE = Type.getType(oshaj.runtime.RuntimeMonitor.class);
	protected static final Type OBJECT_TYPE          = Type.getType(java.lang.Object.class);

	protected static final String ANNOT_INLINE_DESC         = Type.getDescriptor(oshaj.annotation.Inline.class);
	protected static final String ANNOT_THREAD_PRIVATE_DESC = Type.getDescriptor(oshaj.annotation.ThreadPrivate.class);
	protected static final String ANNOT_READ_BY_DESC        = Type.getDescriptor(oshaj.annotation.ReadBy.class);
	protected static final String ANNOT_READ_BY_ALL_DESC    = Type.getDescriptor(oshaj.annotation.ReadByAll.class);
	protected static final String OSHA_EXCEPT_TYPE_NAME     = Type.getInternalName(oshaj.runtime.OshaRuntimeException.class);
	protected static final String SHADOW_FIELD_SUFFIX       = "__osha_state";
	protected static final String STATE_DESC                = STATE_TYPE.getDescriptor();

	protected static final Type[] ARGS_NONE      = new Type[0];
	protected static final Type[] ARGS_INT       = { Type.INT_TYPE };
	protected static final Type[] ARGS_STATE     = { STATE_TYPE };
	protected static final Type[] ARGS_STATE_INT = { STATE_TYPE, Type.INT_TYPE };
	protected static final Type[] ARGS_OBJECT    = { OBJECT_TYPE };

	protected static final Method HOOK_ENTER = new Method("enter", Type.VOID_TYPE, ARGS_INT);
	protected static final Method HOOK_EXIT  = new Method("exit",  Type.VOID_TYPE, ARGS_NONE);

	protected static final Method HOOK_PRIVATE_READ   = new Method("privateRead", Type.VOID_TYPE, ARGS_STATE_INT);
	protected static final Method HOOK_PROTECTED_READ = new Method("sharedRead",  Type.VOID_TYPE, ARGS_STATE_INT);
	protected static final Method HOOK_INLINE_READ    = new Method("inlineRead",  Type.VOID_TYPE, ARGS_STATE);

	protected static final Method HOOK_PRIVATE_WRITE         = new Method("privateWrite",      Type.VOID_TYPE, ARGS_STATE_INT);
	protected static final Method HOOK_PRIVATE_FIRST_WRITE   = new Method("privateFirstWrite", STATE_TYPE,     ARGS_INT);
	protected static final Method HOOK_PROTECTED_WRITE       = new Method("protectedWrite",    Type.VOID_TYPE, ARGS_STATE_INT);
	protected static final Method HOOK_PROTECTED_FIRST_WRITE = new Method("protectedFirstWrite", STATE_TYPE,   ARGS_INT);
	protected static final Method HOOK_PUBLIC_WRITE          = new Method("publicWrite",       Type.VOID_TYPE, ARGS_STATE_INT);
	protected static final Method HOOK_PUBLIC_FIRST_WRITE    = new Method("publicFirstWrite",  STATE_TYPE,     ARGS_INT);
	protected static final Method HOOK_INLINE_WRITE          = new Method("inlineWrite",       Type.VOID_TYPE, ARGS_STATE);
	protected static final Method HOOK_INLINE_FIRST_WRITE    = new Method("inlineFirstWrite",  STATE_TYPE, ARGS_NONE);

	protected static final Method HOOK_ACQUIRE        = new Method("acquire", Type.VOID_TYPE, new Type[] { OBJECT_TYPE, Type.INT_TYPE });
	protected static final Method HOOK_INLINE_ACQUIRE = new Method("inlineAcquire", Type.VOID_TYPE, ARGS_OBJECT);
	protected static final Method HOOK_RELEASE        = new Method("release", Type.VOID_TYPE, ARGS_OBJECT);

	/****************************************************************************/


	public static boolean shouldInstrument(String className) {
		for (String prefix : NONINSTRUMENTED_PREFIXES) {
			if (className.startsWith(prefix)) return false;
		}
		return true;
	}
	
	public static String getDescriptor(String name) {
		return "L" + name.replaceAll("\\.", "/") + ";";
	}

	/**************************************************************************/

	protected String className;
	protected String outerClassDesc = null;
	protected String classDesc;
	protected Type classType;

	public ClassInstrumentor(ClassVisitor cv) {
		super(cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		className = name;
		classDesc = getDescriptor(name);
		classType = Type.getObjectType(name);
		// TODO
		super.visit((version == Opcodes.V1_6 ? Opcodes.V1_5 : version), access, name, signature, superName, interfaces);
	}
	
	// TODO allow the annotations on a class... just send to all methods...
	
	@Override
	public void visitOuterClass(String owner, String name, String desc) {
		outerClassDesc = getDescriptor(owner);
		super.visitOuterClass(owner, name, desc);
	}
	
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		// TODO option to ignore final fields.
		// We make all state fields non-final to be able to set them from outside a constructor
		if (shouldInstrumentField(name, desc)) {
			final FieldVisitor fv = super.visitField(
					access & ~(Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE), // see optimization note in RuntimeMonitor.
					name + SHADOW_FIELD_SUFFIX, STATE_DESC, signature, null
			);
			if (fv != null) {
				fv.visitEnd();
			}
		}
		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		return new MethodInstrumentor(super.visitMethod(access, name, desc, signature, exceptions),
				access, name, desc, this);
	}

	/**
	 * Decides whether a field in class owner with name name and type desc should be instrumented.
	 * Avoids instrumenting this$0, this$1, etc. fields in inner classes.  Just hope there aren't
	 * any dolts who named things this$foo.  I tried. You can -- no collisions (the special this$'s
	 * just become this$0$, this$1$, and so on).
	 * 
	 * @param owner
	 * @param name
	 * @param desc
	 * @return
	 */
	public boolean shouldInstrumentField(String owner, String name, String desc) {
		return shouldInstrument(owner) && shouldInstrumentField(name, desc);
	}
	
	protected boolean shouldInstrumentField(String name, String desc) {
		return (outerClassDesc == null 
				|| ! (name.startsWith("this$") && desc.equals(outerClassDesc)));
	}
}
