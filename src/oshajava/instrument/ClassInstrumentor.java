package oshajava.instrument;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.ModuleSpecNotFoundException;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.support.org.objectweb.asm.AnnotationVisitor;
import oshajava.support.org.objectweb.asm.ClassAdapter;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.FieldVisitor;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.GeneratorAdapter;
import oshajava.support.org.objectweb.asm.commons.JSRInlinerAdapter;
import oshajava.support.org.objectweb.asm.commons.Method;


public class ClassInstrumentor extends ClassAdapter {

	/* TODO: Options
	 * 
	 * treat unannotated methods as inline or read-by-all
	 * (or in other words --default-annotation)
	 * 
	 * instrument final fields or not
	 * 
	 */

	protected static final Type BIT_VECTOR_INT_SET_TYPE = Type.getType(oshajava.util.intset.BitVectorIntSet.class);
	protected static final Type STRING_TYPE          = Type.getType(java.lang.String.class);
	protected static final Type STACK_TYPE           = Type.getType(oshajava.runtime.Stack.class);
	protected static final Type STATE_TYPE           = Type.getType(oshajava.runtime.State.class);
	protected static final Type THREAD_STATE_TYPE    = Type.getType(oshajava.runtime.ThreadState.class);
	protected static final Type RUNTIME_MONITOR_TYPE = Type.getType(oshajava.runtime.RuntimeMonitor.class);
	protected static final Type OBJECT_TYPE          = Type.getType(java.lang.Object.class);

	protected static final String ANNOT_INLINE_DESC         = Type.getDescriptor(oshajava.annotation.Inline.class);
	protected static final String ANNOT_READER_DESC = Type.getDescriptor(oshajava.annotation.Reader.class);
	protected static final String ANNOT_WRITER_DESC = Type.getDescriptor(oshajava.annotation.Writer.class);
	protected static final String ANNOT_MODULE_MEMBER_DESC = Type.getDescriptor(oshajava.annotation.Member.class);
	
	protected static final String OSHA_EXCEPT_TYPE_NAME     = Type.getInternalName(oshajava.runtime.exceptions.OshaRuntimeException.class);
	protected static final String SHADOW_FIELD_SUFFIX       = "__osha_state";
	protected static final String STATE_DESC                = STATE_TYPE.getDescriptor();
	protected static final String STACK_FIELD       		= "stack";
	protected static final String WRITER_CACHE_FIELD       	= "writerCache";
	protected static final String ID_FIELD       			= "id";
	protected static final String CURRENT_STATE_FIELD       = "state";
	protected static final String THREAD_FIELD              = "thread";
	protected static final String OBJECT_WITH_STATE_NAME    = Type.getInternalName(oshajava.runtime.ObjectWithState.class);
	protected static final String SHADOW_INIT_METHOD_PREFIX = "__osha_shadow_field_initer";

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
	
	protected static final Method STATE_STACK_ID  = new Method("getStackID",  Type.INT_TYPE, ARGS_NONE);
	protected static final Method CONTAINS_METHOD  = new Method("contains", Type.BOOLEAN_TYPE,  new Type[] { Type.INT_TYPE });

	protected static final Method HOOK_ENTER = new Method("enter", THREAD_STATE_TYPE, ARGS_INT);
	protected static final Method HOOK_EXIT  = new Method("exit",  Type.VOID_TYPE, new Type[] { THREAD_STATE_TYPE });

	protected static final Method HOOK_THREAD_STATE = new Method("getThreadState", THREAD_STATE_TYPE, ARGS_NONE);
	protected static final Method HOOK_CURRENT_STATE = new Method("getCurrentState", STATE_TYPE, ARGS_NONE);

	protected static final Method HOOK_READ  = new Method("checkReadSlowPath",  Type.VOID_TYPE, new Type[] { STATE_TYPE, STATE_TYPE });
	
//	protected static final Method HOOK_NEW_ARRAY       = new Method("newArray",      Type.VOID_TYPE, ARGS_INT_OBJECT);
//	protected static final Method HOOK_NEW_MULTI_ARRAY = new Method("newMultiArray", Type.VOID_TYPE, ARGS_OBJECT_INT);
	protected static final Method HOOK_ARRAY_LOAD      = new Method("arrayRead",     Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, THREAD_STATE_TYPE, BIT_VECTOR_INT_SET_TYPE});
	protected static final Method HOOK_ARRAY_STORE     = new Method("arrayWrite",    Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, STATE_TYPE, THREAD_STATE_TYPE});
	protected static final Method HOOK_COARSE_ARRAY_LOAD  = new Method("coarseArrayRead",     Type.VOID_TYPE, new Type[] {OBJECT_TYPE, THREAD_STATE_TYPE, BIT_VECTOR_INT_SET_TYPE} );
	protected static final Method HOOK_COARSE_ARRAY_STORE = new Method("coarseArrayWrite",    Type.VOID_TYPE, new Type[] {OBJECT_TYPE, STATE_TYPE, THREAD_STATE_TYPE});

	protected static final Method HOOK_ACQUIRE        = new Method("acquire", Type.VOID_TYPE, new Type[] {OBJECT_TYPE, THREAD_STATE_TYPE, STATE_TYPE});
	protected static final Method HOOK_RELEASE        = new Method("release", Type.VOID_TYPE, ARGS_OBJECT_THREAD);
	protected static final Method HOOK_PREWAIT        = new Method("prewait", Type.INT_TYPE, ARGS_OBJECT_THREAD);
	protected static final Method HOOK_POSTWAIT       = new Method("postwait", Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, THREAD_STATE_TYPE, STATE_TYPE});

	protected static final Method INSTANCE_SHADOW_INIT_METHOD = new Method(SHADOW_INIT_METHOD_PREFIX, Type.VOID_TYPE, ARGS_NONE);
	protected static final Method STATIC_SHADOW_INIT_METHOD = new Method(SHADOW_INIT_METHOD_PREFIX + "_clinit", Type.VOID_TYPE, ARGS_NONE);
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
	protected String packageName;
	protected final ClassLoader loader;
	private final ArrayList<String> instanceShadowFields = new ArrayList<String>();
	private final ArrayList<String> staticShadowFields = new ArrayList<String>();

	public ClassInstrumentor(ClassVisitor cv, ClassLoader loader, InstrumentationAgent.Options opts) {
		super(cv);
		this.opts = opts;
		this.loader = loader;
	}
	
	private ModuleSpec getModule() throws ModuleSpecNotFoundException {
		if (module == null) {
			module = Spec.getModule(packageName + ModuleSpec.DEFAULT_NAME, loader);
		}
		Util.assertTrue(module != null, "No module specified for %s.", className);
		return module;
	}
	
	/**
	 * Decides whether a given class has a shadow field (inherited or declared) for a particular field.
	 * 
	 * @param cls
	 * @param name
	 * @return
	 */
	private static boolean hasShadowField(Class<?> cls, String name) {
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
	private static void getAllFieldsHelper(Class<?> cls, Set<String> usedIds, List<Field> fields) {
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
	private static List<Field> getAllFields(Class<?> cls) {
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
		    
		    int newAccess;
		    if ((classAccess & Opcodes.ACC_INTERFACE) != 0) {
		        
		        // All fields in interfaces must be final, so our new shadow field
		        // must obey this. This might seem silly because we'll never be
		        // writing to final fields, but we can't tell when we see an access
		        // whether the field is in an interface (and thus whether we should
		        // instrument the access). Adding these unused shadow fields
		        // regardless alleviates this concern.
		        newAccess = access & ~Opcodes.ACC_VOLATILE;
		        
		    } else {
		        newAccess = access & ~(Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE);
		    }
		    
			final FieldVisitor fv = super.visitField(
					newAccess,
					name + SHADOW_FIELD_SUFFIX, STATE_DESC, null, null
			);
			if (fv != null) {
				fv.visitEnd();
			}
			((access & Opcodes.ACC_STATIC)  == 0 ? instanceShadowFields : staticShadowFields).add(name + SHADOW_FIELD_SUFFIX);
		}
	}
	
	/**
	 * Get a Class object for a qualified name using a minimal class loader.
	 * 
	 * @param name
	 * @return a Class object
	 */
	private static Class<?> classForName(String name) {
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
		final int lastSep = name.lastIndexOf('/');
		packageName = lastSep == -1 ? "" : name.substring(0, lastSep + 1);
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
			Class<?> superclass = classForName(Type.getObjectType(superName).getClassName());
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
					try {
						ClassInstrumentor.this.module = Spec.getModule((String)name, loader);
					} catch (ModuleSpecNotFoundException e) {
						throw e.wrap();
					}
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

	private boolean visitedClinit = false;
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if ((access & Opcodes.ACC_NATIVE) == 0) {
		    MethodVisitor chain = super.visitMethod(access, name, desc, signature, exceptions);
		    if (!name.equals("<clinit>")) {
		    	visitedClinit = true;
		    	// (Not instrumenting class initializers avoids balooning the size
		    	//  of large literal table constructions. It also makes sense, I
		    	//  think, because no "communication" should occur (semantically)
		    	//  from class loading.) FIXME
			    chain = new HandlerSorterAdapter(chain, access, name, desc, signature, exceptions);
//			    Util.log(name);
		    	try {
		    		chain = new MethodInstrumentor(chain, access, name, desc, this, getModule());
		    	} catch (ModuleSpecNotFoundException e) {
		    		throw e.wrap();
		    	}
			    chain = new JSRInlinerAdapter(chain, access, name, desc, signature, exceptions);
			    return chain;
		    } else {
		    	return new StaticShadowInitInserter(chain, access, name, desc, classType);
		    }
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
		return (outerClassDesc == null 
				|| ! (name.startsWith("this$") && desc.equals(outerClassDesc)));
	}
	

	
	@Override
	public void visitEnd() {
		// FIXME Generate a method that initializes all shadow fields belonging to this class.
		// instance.
		if (!instanceShadowFields.isEmpty()) {
//	    	Util.log("gen " + INSTANCE_SHADOW_INIT_METHOD.getName());
			GeneratorAdapter instance = new GeneratorAdapter(Opcodes.ACC_PRIVATE, INSTANCE_SHADOW_INIT_METHOD, 
					super.visitMethod(Opcodes.ACC_PROTECTED, INSTANCE_SHADOW_INIT_METHOD.getName(), INSTANCE_SHADOW_INIT_METHOD.getDescriptor(), null, null));
			instance.visitCode();
			
			int varCurrentState = instance.newLocal(STATE_TYPE);
			instance.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_CURRENT_STATE);
			instance.storeLocal(varCurrentState);
			// call super.initer()
			if (InstrumentationAgent.shouldInstrument(superName)) {
				instance.loadThis();
				instance.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, INSTANCE_SHADOW_INIT_METHOD.getName(), INSTANCE_SHADOW_INIT_METHOD.getDescriptor());
			}
			
			for (String shadow : instanceShadowFields) {
				instance.loadThis();
				instance.loadLocal(varCurrentState);
				instance.visitFieldInsn(Opcodes.PUTFIELD, className, shadow, STATE_DESC);
			}
			instance.returnValue();
			instance.visitMaxs(2, 1);
			instance.visitEnd();
		}
		
		// static.
		if (!staticShadowFields.isEmpty()) {
//	    	Util.log("gen " + STATIC_SHADOW_INIT_METHOD.getName());
			GeneratorAdapter stat = new GeneratorAdapter(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, STATIC_SHADOW_INIT_METHOD, 
					super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, STATIC_SHADOW_INIT_METHOD.getName(), STATIC_SHADOW_INIT_METHOD.getDescriptor(), null, null));
			stat.visitCode();
			int varCurrentState = stat.newLocal(STATE_TYPE);
			stat.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_CURRENT_STATE);
			stat.storeLocal(varCurrentState);
			for (String shadow : staticShadowFields) {
				stat.loadLocal(varCurrentState);
				stat.visitFieldInsn(Opcodes.PUTSTATIC, className, shadow, STATE_DESC);
			}
			stat.returnValue();
			stat.visitMaxs(1, 1);
			stat.visitEnd();
		}
		
		if (!visitedClinit) {
			final int acc = Opcodes.ACC_STATIC;
			final String name = "<clinit>";
			final String desc = "()V";
			GeneratorAdapter clinit = new GeneratorAdapter(
					new StaticShadowInitInserter(
							super.visitMethod(acc, name, desc, null, null),
							acc, name, desc, classType),
					acc, name, desc
			);
			
			clinit.visitCode();
			clinit.visitMaxs(0, 0);
			clinit.visitEnd();
		}

	}
	
}
