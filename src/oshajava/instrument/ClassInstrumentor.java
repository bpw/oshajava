package oshajava.instrument;

import oshajava.sourceinfo.MethodTable;
import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.ModuleSpecNotFoundException;
import oshajava.sourceinfo.Spec;
import oshajava.support.org.objectweb.asm.AnnotationVisitor;
import oshajava.support.org.objectweb.asm.ClassAdapter;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.FieldVisitor;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.Method;
import oshajava.support.org.objectweb.asm.commons.JSRInlinerAdapter;
import oshajava.support.acme.util.Util;
import java.lang.Class;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;



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

	protected static final Type STRING_TYPE          = Type.getType(java.lang.String.class);
	protected static final Type STATE_TYPE           = Type.getType(oshajava.runtime.State.class);
	protected static final Type THREAD_STATE_TYPE    = Type.getType(oshajava.runtime.ThreadState.class);
	protected static final Type RUNTIME_MONITOR_TYPE = Type.getType(oshajava.runtime.RuntimeMonitor.class);
	protected static final Type OBJECT_TYPE          = Type.getType(java.lang.Object.class);

	protected static final String ANNOT_INLINE_DESC         = Type.getDescriptor(oshajava.annotation.Inline.class);
	protected static final String ANNOT_READER_DESC = Type.getDescriptor(oshajava.annotation.Reader.class);
	protected static final String ANNOT_WRITER_DESC = Type.getDescriptor(oshajava.annotation.Writer.class);
	protected static final String ANNOT_MODULE_MEMBER_DESC = Type.getDescriptor(oshajava.annotation.Member.class);
	
	protected static final String OSHA_EXCEPT_TYPE_NAME     = Type.getInternalName(oshajava.runtime.OshaRuntimeException.class);
	protected static final String SHADOW_FIELD_SUFFIX       = "__osha_state";
	protected static final String STATE_DESC                = STATE_TYPE.getDescriptor();
	protected static final String CURRENT_STATE_FIELD       = "currentState";
	protected static final String THREAD_FIELD              = "thread";
	protected static final String OBJECT_WITH_STATE_NAME    = Type.getInternalName(oshajava.runtime.ObjectWithState.class);

	protected static final Type[] ARGS_NONE      = new Type[0];
	protected static final Type[] ARGS_INT       = { Type.INT_TYPE };
	protected static final Type[] ARGS_STATE     = { STATE_TYPE };
	protected static final Type[] ARGS_STATE_THREAD     = { STATE_TYPE, THREAD_STATE_TYPE };
	protected static final Type[] ARGS_STATE_INT = { STATE_TYPE, Type.INT_TYPE };
	protected static final Type[] ARGS_OBJECT_INT = { OBJECT_TYPE, Type.INT_TYPE };
	protected static final Type[] ARGS_OBJECT_INT_STATE = { OBJECT_TYPE, Type.INT_TYPE, STATE_TYPE };
	protected static final Type[] ARGS_INT_OBJECT = { Type.INT_TYPE, OBJECT_TYPE };
	protected static final Type[] ARGS_INT_OBJECT_THREAD = { Type.INT_TYPE, OBJECT_TYPE, THREAD_STATE_TYPE };
	protected static final Type[] ARGS_OBJECT    = { OBJECT_TYPE };
	protected static final Type[] ARGS_OBJECT_THREAD    = { OBJECT_TYPE, THREAD_STATE_TYPE };
	protected static final Type[] ARGS_OBJECT_STATE    = { OBJECT_TYPE, STATE_TYPE };
	
	protected static final Method HOOK_ENTER = new Method("enter", THREAD_STATE_TYPE, ARGS_INT);
	protected static final Method HOOK_EXIT  = new Method("exit",  Type.VOID_TYPE, ARGS_NONE);

	protected static final Method HOOK_THREAD_STATE = new Method("getThreadState", THREAD_STATE_TYPE, ARGS_NONE);

	//	protected static final Method HOOK_PRIVATE_READ   = new Method("privateRead", Type.VOID_TYPE, ARGS_STATE_INT);
	protected static final Method HOOK_READ  = new Method("read",  Type.VOID_TYPE, ARGS_STATE_THREAD);
	protected static final Method HOOK_RECORD_READ  = new Method("recordRead",  Type.VOID_TYPE, ARGS_STATE_THREAD);
//	protected static final Method HOOK_WRITE = new Method("write", STATE_TYPE, ARGS_NONE);
	
//	protected static final Method HOOK_NEW_ARRAY       = new Method("newArray",      Type.VOID_TYPE, ARGS_INT_OBJECT);
//	protected static final Method HOOK_NEW_MULTI_ARRAY = new Method("newMultiArray", Type.VOID_TYPE, ARGS_OBJECT_INT);
	protected static final Method HOOK_ARRAY_LOAD      = new Method("arrayRead",     Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, THREAD_STATE_TYPE});
	protected static final Method HOOK_ARRAY_STORE     = new Method("arrayWrite",    Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, STATE_TYPE, THREAD_STATE_TYPE});
	protected static final Method HOOK_COARSE_ARRAY_LOAD  = new Method("coarseArrayRead",     Type.VOID_TYPE, ARGS_OBJECT_THREAD);
	protected static final Method HOOK_COARSE_ARRAY_STORE = new Method("coarseArrayWrite",    Type.VOID_TYPE, new Type[] {OBJECT_TYPE, STATE_TYPE, THREAD_STATE_TYPE});

	protected static final Method HOOK_ACQUIRE        = new Method("acquire", Type.VOID_TYPE, new Type[] {OBJECT_TYPE, THREAD_STATE_TYPE, STATE_TYPE});
	protected static final Method HOOK_RELEASE        = new Method("release", Type.VOID_TYPE, ARGS_OBJECT_THREAD);
	protected static final Method HOOK_PREWAIT        = new Method("prewait", Type.INT_TYPE, ARGS_OBJECT_THREAD);
	protected static final Method HOOK_POSTWAIT       = new Method("postwait", Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, THREAD_STATE_TYPE, STATE_TYPE});

	/****************************************************************************/

	public static String getDescriptor(String name) {
		return "L" + name.replace('.', '/') + ";";
	}

	/**************************************************************************/

	protected int classAccess;
	protected String className;
	protected String outerClassDesc = null;
	protected String classDesc;
	protected Type classType;
	protected InstrumentationAgent.Options opts;
	protected String superName;
	protected ModuleSpec module;
	protected Set<String> shadowedInheritedFields;

	public ClassInstrumentor(ClassVisitor cv, InstrumentationAgent.Options opts) {
		super(cv);
		this.opts = opts;
	}
	
	/**
	 * Decides whether a given class has a shadow field (inherited or declared) for a particular field.
	 * 
	 * @param cls
	 * @param name
	 * @return
	 */
	private static boolean hasShadowField(Class cls, String name) {
		try {
			cls.getField(name + SHADOW_FIELD_SUFFIX);
		} catch (SecurityException e) {
			Util.fail(e);
			return false;
		} catch (NoSuchFieldException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Recursively gathers all the fields of a class into a list. The set of usedIds contains
	 * strings indicating already-seen fields, used to avoid duplicates in the output list.
	 * 
	 * @param cls
	 * @param usedIds
	 * @param fields
	 */
	private static void getAllFieldsHelper(Class cls, Set<String> usedIds, List<Field> fields) {
		if (cls == null) {
			
			// Base case: top of class heirarchy has no fields.
			
		} else {
			
			// Add all inherited fields.
			getAllFieldsHelper(cls.getSuperclass(), usedIds, fields);

			// Add all fields declared here, if they aren't redefinitions.
			for (Field fld : cls.getDeclaredFields()) {
				if (usedIds.add(fld.getName() + " " + Type.getDescriptor(fld.getType()))) {
					// Not a duplicate.
					fields.add(fld);
				}
			}
			
		}
	}
	
	/**
	 * Returns a list of all the fields (declared or inherited) in a class. Existing methods
	 * won't suffice because they list either only declared fields or only public fields.
	 * 
	 * @param cls
	 * @return
	 */
	private static List<Field> getAllFields(Class cls) {
		Set<String> usedIds = new HashSet<String>();
		List<Field> fields = new LinkedList<Field>();
		getAllFieldsHelper(cls, usedIds, fields);
		return fields;
	}

	/**
	 * Add a shadow field for a given field.
	 * 
	 * @param access
	 * @param name
	 * @param desc
	 */
	private void addShadowField(int access, String name, String desc) {
		if (shouldInstrumentField(name, desc)) {
			final FieldVisitor fv = super.visitField(
					access & ~(Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE),
					name + SHADOW_FIELD_SUFFIX, STATE_DESC, null, null
			);
			if (fv != null) {
				fv.visitEnd();
			}
		}
	}
	
	/**
	 * Get a Class object for a qualified name using a minimal class loader.
	 * 
	 * @param name
	 * @return a Class object
	 */
	private static Class classForName(String name) {
		try {
			return Class.forName(name, true, null);
		} catch (ClassNotFoundException ex) {
			try {
				return Class.forName(name, true, ClassLoader.getSystemClassLoader());
			} catch (ClassNotFoundException e) {
				return null;
			}
		}
	}
	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		className = name;
		classDesc = getDescriptor(name);
		classType = Type.getObjectType(name);
		classAccess = access;
		this.superName = superName;
		if (opts.coarseFieldStates && (access & Opcodes.ACC_INTERFACE) == 0 && (superName == null || superName.equals("java/lang/Object"))) {
			superName = Type.getType(oshajava.runtime.ObjectWithState.class).getInternalName();
		}

		// TODO 5/6
		super.visit((version == Opcodes.V1_6 ? Opcodes.V1_5 : version), access, name, signature, superName, interfaces);
//		Util.log("class " + name + " extends " + superName);
		
		// Ensure all fields here (including inherited ones) are shadowed. First, check whether our
		// superclass is instrumented.
		shadowedInheritedFields = new HashSet<String>();
		if (!InstrumentationAgent.shouldInstrument(superName)) {
		
			// Get this class' superclass to find inherited fields that need to be shadowed.
			Class superclass = classForName(Type.getObjectType(superName).getClassName());
			if (superclass == null) {
				Util.fail("superclass not found: " + Type.getObjectType(superName).getClassName());
			}
			
			// Shadow any unshadowed inherited fields. Keep track of which fields we shadow here
			// to avoid conflicts with fields declared here.
			for (Field fld : getAllFields(superclass)) {
				if (!hasShadowField(superclass, name)) {
					addShadowField(fld.getModifiers(), fld.getName(), Type.getDescriptor(fld.getType()));
					shadowedInheritedFields.add(fld.getName());
				}
			}

		}
		
	}
	
	// TODO allow READER/WRITER annotations on a class... just send to all methods...
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if (ANNOT_MODULE_MEMBER_DESC.equals(desc)) {
			return new AnnotationVisitor() {
				public void visit(String name, Object value) { // throws ModuleSpecNotFoundException
					ClassInstrumentor.this.module = Spec.getModule((String)name);
				}
				public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
				public AnnotationVisitor visitArray(String name) { return null; }
				public void visitEnd() { }
				public void visitEnum(String name, String desc, String value) { }
			};
		} else {
			return null;
		}
	}
	
	@Override
	public void visitOuterClass(String owner, String name, String desc) {
		outerClassDesc = getDescriptor(owner);
		super.visitOuterClass(owner, name, desc);
	}
	
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		if (  (!opts.coarseFieldStates || (access & Opcodes.ACC_STATIC) != 0)) {
			// TODO option to ignore final fields. how?
			// We make all state fields non-final to be able to set them from outside a constructor
        	if (!shadowedInheritedFields.contains(name)) {
        		addShadowField(access, name, desc);
        	}
		}
		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		Util.assertTrue(module != null, "No module specified for %s.", className);
		if ((access & Opcodes.ACC_NATIVE) == 0) {
		    MethodVisitor chain = super.visitMethod(access, name, desc, signature, exceptions);
		    chain = new HandlerSorterAdapter(chain, access, name, desc, signature, exceptions);
		    chain = new MethodInstrumentor(chain, access, name, desc, this, module);
		    chain = new JSRInlinerAdapter(chain, access, name, desc, signature, exceptions);
		    return chain;
		} else {
			return super.visitMethod(access, name, desc, signature, exceptions);
		}
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
		return InstrumentationAgent.shouldInstrument(owner) && shouldInstrumentField(name, desc);
	}
	
	protected boolean shouldInstrumentField(String name, String desc) {
		return (classAccess & Opcodes.ACC_INTERFACE) == 0 && // don't create shadows for interface fields
		        (outerClassDesc == null 
				|| ! (name.startsWith("this$") && desc.equals(outerClassDesc)));
	}
	
}
