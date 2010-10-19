package oshajava.instrument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import oshajava.runtime.Config;
import oshajava.spec.ModuleMap;
import oshajava.spec.ModuleMap.MissingEntryException;
import oshajava.spec.ModuleSpec;
import oshajava.spec.NullModuleSpec;
import oshajava.spec.Spec;
import oshajava.spec.exceptions.ModuleMapNotFoundException;
import oshajava.spec.exceptions.ModuleSpecNotFoundException;
import oshajava.spec.names.FieldDescriptor;
import oshajava.spec.names.MethodDescriptor;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.support.acme.util.StringMatchResult;
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

	protected static final Type BIT_VECTOR_INT_SET_TYPE = Type.getType(oshajava.util.BitVectorIntSet.class);
	protected static final Type STRING_TYPE             = Type.getType(java.lang.String.class);
	protected static final Type STACK_TYPE              = Type.getType(oshajava.runtime.Stack.class);
	protected static final Type STATE_TYPE              = Type.getType(oshajava.runtime.State.class);
	protected static final Type THREAD_STATE_TYPE       = Type.getType(oshajava.runtime.ThreadState.class);
	protected static final Type RUNTIME_MONITOR_TYPE    = Type.getType(oshajava.runtime.RuntimeMonitor.class);
	protected static final Type OBJECT_TYPE             = Type.getType(java.lang.Object.class);
	protected static final Type THREAD_TYPE             = Type.getType(java.lang.Thread.class);

	protected static final String ANNOT_INLINE_DESC        = Type.getDescriptor(oshajava.annotation.Inline.class);
	protected static final String ANNOT_READER_DESC        = Type.getDescriptor(oshajava.annotation.Reader.class);
	protected static final String ANNOT_WRITER_DESC        = Type.getDescriptor(oshajava.annotation.Writer.class);
	protected static final String ANNOT_MODULE_MEMBER_DESC = Type.getDescriptor(oshajava.annotation.Module.class);
	
	protected static final String OSHA_EXCEPT_TYPE_NAME     = Type.getInternalName(oshajava.runtime.exceptions.OshaRuntimeException.class);
	protected static final String SHADOW_FIELD_SUFFIX       = "__osha_state";
	protected static final String STATE_DESC                = STATE_TYPE.getDescriptor();
	protected static final String STACK_FIELD       		= "stack";
	protected static final String WRITER_CACHE_FIELD       	= "writerCache";
	protected static final String ID_FIELD       			= "id";
	protected static final String CURRENT_STATE_FIELD       = "state";
	protected static final String THREAD_FIELD              = "thread";
	protected static final String SHADOW_INIT_METHOD_PREFIX = "__osha_shadow_field_initer";

	protected static final ObjectTypeDescriptor OBJECT_WITH_STATE = TypeDescriptor.ofClass(oshajava.runtime.ObjectWithState.class.getCanonicalName());

	protected static final Type[] ARGS_NONE              = new Type[0];
	protected static final Type[] ARGS_INT               = { Type.INT_TYPE };
	protected static final Type[] ARGS_STATE             = { STATE_TYPE };
	protected static final Type[] ARGS_STATE_THREAD      = { STATE_TYPE, THREAD_STATE_TYPE };
	protected static final Type[] ARGS_STATE_INT         = { STATE_TYPE, Type.INT_TYPE };
	protected static final Type[] ARGS_OBJECT_INT        = { OBJECT_TYPE, Type.INT_TYPE };
	protected static final Type[] ARGS_OBJECT_INT_STATE  = { OBJECT_TYPE, Type.INT_TYPE, STATE_TYPE };
	protected static final Type[] ARGS_INT_OBJECT        = { Type.INT_TYPE, OBJECT_TYPE };
	protected static final Type[] ARGS_INT_OBJECT_THREAD = { Type.INT_TYPE, OBJECT_TYPE, THREAD_STATE_TYPE };
	protected static final Type[] ARGS_OBJECT            = { OBJECT_TYPE };
	protected static final Type[] ARGS_OBJECT_THREAD     = { OBJECT_TYPE, THREAD_STATE_TYPE };
	protected static final Type[] ARGS_OBJECT_STATE      = { OBJECT_TYPE, STATE_TYPE };
	
	protected static final String STACKTRACE_FIELD_SUFFIX   = "__osha_stacktrace";
	protected static final String STACKTRACE_DESC           = "[Ljava/lang/StackTraceElement;";
	protected static final Type   STACKTRACE_TYPE           = Type.getType(STACKTRACE_DESC);
	
	protected static final Method CURRENTTHREAD_METHOD = new Method("currentThread", THREAD_TYPE, ARGS_NONE);
	protected static final Method GETSTACKTRACE_METHOD = new Method("getStackTrace", STACKTRACE_TYPE, ARGS_NONE);
	protected static final Method STATE_STACK_ID       = new Method("getStackID",    Type.INT_TYPE, ARGS_NONE);
	protected static final Method CONTAINS_METHOD      = new Method("contains",      Type.BOOLEAN_TYPE,  new Type[] { Type.INT_TYPE });

	protected static final Method HOOK_ENTER        = new Method("enter",       THREAD_STATE_TYPE, ARGS_INT);
	protected static final Method HOOK_EXIT         = new Method("exit",        Type.VOID_TYPE, new Type[] { THREAD_STATE_TYPE });
	protected static final Method HOOK_ENTER_CLINIT = new Method("enterClinit", THREAD_STATE_TYPE, ARGS_NONE);

	protected static final Method HOOK_THREAD_STATE  = new Method("getThreadState",   THREAD_STATE_TYPE, ARGS_NONE);
	protected static final Method HOOK_CURRENT_STATE = new Method("getCurrentState",  STATE_TYPE,        ARGS_NONE);
	protected static final Method HOOK_CLINIT_STATE  = new Method("classInitializer", STATE_TYPE,        new Type[] { THREAD_STATE_TYPE });

	protected static final Method HOOK_READ             = new Method("checkFieldRead", Type.VOID_TYPE, new Type[] { STATE_TYPE, STATE_TYPE, Type.getType(String.class) });
	protected static final Method HOOK_READ_STACK_TRACE = new Method("checkFieldRead", Type.VOID_TYPE, new Type[] { STATE_TYPE, STATE_TYPE, Type.getType(String.class), STACKTRACE_TYPE });
	
	protected static final Method HOOK_ARRAY_LOAD         = new Method("arrayRead",        Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, THREAD_STATE_TYPE, BIT_VECTOR_INT_SET_TYPE});
	protected static final Method HOOK_ARRAY_STORE        = new Method("arrayWrite",       Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, STATE_TYPE, THREAD_STATE_TYPE});
	protected static final Method HOOK_COARSE_ARRAY_LOAD  = new Method("coarseArrayRead",  Type.VOID_TYPE, new Type[] {OBJECT_TYPE, THREAD_STATE_TYPE, BIT_VECTOR_INT_SET_TYPE} );
	protected static final Method HOOK_COARSE_ARRAY_STORE = new Method("coarseArrayWrite", Type.VOID_TYPE, new Type[] {OBJECT_TYPE, STATE_TYPE, THREAD_STATE_TYPE});

	protected static final Method HOOK_ACQUIRE  = new Method("acquire",  Type.VOID_TYPE, new Type[] {OBJECT_TYPE, THREAD_STATE_TYPE, STATE_TYPE});
	protected static final Method HOOK_RELEASE  = new Method("release",  Type.VOID_TYPE, ARGS_OBJECT_THREAD);
	protected static final Method HOOK_PREWAIT  = new Method("prewait",  Type.INT_TYPE,  ARGS_OBJECT_THREAD);
	protected static final Method HOOK_POSTWAIT = new Method("postwait", Type.VOID_TYPE, new Type[] {OBJECT_TYPE, Type.INT_TYPE, THREAD_STATE_TYPE, STATE_TYPE});

	protected static final Method INSTANCE_SHADOW_INIT_METHOD = new Method(SHADOW_INIT_METHOD_PREFIX,             Type.VOID_TYPE, ARGS_NONE);
	protected static final Method STATIC_SHADOW_INIT_METHOD   = new Method(SHADOW_INIT_METHOD_PREFIX + "_clinit", Type.VOID_TYPE, ARGS_NONE);

	protected static final Method HOOK_COUNT_READ = new Method("countRead", Type.VOID_TYPE, ARGS_NONE);
	protected static final Method HOOK_COUNT_COMM = new Method("countComm", Type.VOID_TYPE, ARGS_NONE);

	/****************************************************************************/

	public static String getDescriptor(String name) {
		return "L" + name.replace('.', '/') + ";";
	}

	/**************************************************************************/

	protected int classAccess;
	protected ObjectTypeDescriptor classType, superType;
	protected Set<String> shadowedInheritedFields;
	protected final ClassLoader loader;
	private final ArrayList<FieldDescriptor> instanceShadowedFields = new ArrayList<FieldDescriptor>();
	private final ArrayList<FieldDescriptor> staticShadowedFields = new ArrayList<FieldDescriptor>();
	private ModuleMap moduleMap;

	public ClassInstrumentor(ClassVisitor cv, ClassLoader loader) {
		super(cv);
		this.loader = loader;
	}
	
//	/**
//	 * Decides whether a given class has a shadow field (inherited or declared) for a particular field.
//	 * 
//	 * @param cls
//	 * @param name
//	 * @return
//	 */
//	private static boolean hasShadowField(Class<?> cls, String name) {
//		try {
//			cls.getField(name + SHADOW_FIELD_SUFFIX);
//		} catch (SecurityException e) {
//			Assert.fail(e);
//			return false;
//		} catch (NoSuchFieldException e) {
//			return false;
//		}
//		return true;
//	}
//	
//	/**
//	 * Recursively gathers all the fields of a class into a list. The set of usedIds contains
//	 * strings indicating already-seen fields, used to avoid duplicates in the output list.
//	 * 
//	 * @param cls
//	 * @param usedIds
//	 * @param fields
//	 */
//	private static void getAllFieldsHelper(Class<?> cls, Set<String> usedIds, List<Field> fields) {
//		if (cls == null) {
//			
//			// Base case: top of class heirarchy has no fields.
//			
//		} else {
//			
//			// Add all inherited fields.
//			getAllFieldsHelper(cls.getSuperclass(), usedIds, fields);
//
//			// Add all fields declared here, if they aren't redefinitions.
//			for (Field fld : cls.getDeclaredFields()) {
//				if (usedIds.add(fld.getName() + " " + Type.getDescriptor(fld.getType()))) {
//					// Not a duplicate.
//					fields.add(fld);
//				}
//			}
//			
//		}
//	}
//	
//	/**
//	 * Returns a list of all the fields (declared or inherited) in a class. Existing methods
//	 * won't suffice because they list either only declared fields or only public fields.
//	 * 
//	 * @param cls
//	 * @return
//	 */
//	private static List<Field> getAllFields(Class<?> cls) {
//		Set<String> usedIds = new HashSet<String>();
//		List<Field> fields = new LinkedList<Field>();
//		getAllFieldsHelper(cls, usedIds, fields);
//		return fields;
//	}

	/**
	 * Add a shadow field for a given field.
	 * 
	 * @param access
	 * @param name
	 * @param desc
	 */
	private void addShadowField(FieldDescriptor fd) {
		if (Filter.shouldInstrument(fd) && !(fd.isStatic() ? staticShadowedFields : instanceShadowedFields).contains(fd)) {
		    
		    int newAccess;
		    if ((classAccess & Opcodes.ACC_INTERFACE) != 0) {
		        
		        // All fields in interfaces must be final, so our new shadow field
		        // must obey this. This might seem silly because we'll never be
		        // writing to final fields, but we can't tell when we see an access
		        // whether the field is in an interface (and thus whether we should
		        // instrument the access). Adding these unused shadow fields
		        // regardless alleviates this concern.
		        newAccess = fd.getAccessFlags() & ~Opcodes.ACC_VOLATILE;
		        
		    } else {
		        newAccess = fd.getAccessFlags() & ~(Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE);
		    }
		    
		    // Option to make all shadows volatile.
	        if (Agent.volatileShadowOption.get()) {
	        	newAccess |= Opcodes.ACC_VOLATILE;
	        }

	        final FieldVisitor fv = super.visitField(
					newAccess,
					fd.getFieldName() + SHADOW_FIELD_SUFFIX, STATE_DESC, null, null
			);
			if (fv != null) {
				fv.visitEnd();
			}
			(fd.isStatic() ? staticShadowedFields : instanceShadowedFields).add(fd);
			
			// Add stack trace field if requested.
			if (Config.stackTracesOption.get()) {
			    final FieldVisitor stfv = super.visitField(
    					newAccess,
    					fd.getFieldName() + STACKTRACE_FIELD_SUFFIX, STACKTRACE_DESC, null, null
    			);
    			if (stfv != null) {
    				stfv.visitEnd();
    			}
			}
		}
	}
	
//	/**
//	 * Get a Class object for a qualified name using a minimal class loader.
//	 * 
//	 * @param name
//	 * @return a Class object
//	 */
//	private static Class<?> classForName(String name) {
//		try {
//			return Class.forName(name, false, ClassLoader.getSystemClassLoader());
//		} catch (ClassNotFoundException ex) {
//			try {
//				return Class.forName(name, true, ClassLoader.getSystemClassLoader());
//			} catch (ClassNotFoundException e) {
//				return null;
//			}
//		}
//	}
//	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		// Assume that this is *not* an inner class even if it contains $.  We will rectify this in visitOuterClass()
		// if this is actually an inner class.
		classType = TypeDescriptor.ofClass(name);
		classAccess = access;
		
		// The real super type of this class.
		this.superType = TypeDescriptor.ofClass(superName);
		
		// If we're doing object-granularity tracking, we put in some shim right before Object.
		if (Config.objectTrackingOption.get() == Config.Granularity.COARSE && (access & Opcodes.ACC_INTERFACE) == 0 && 
				(superName == null || superType.equals(TypeDescriptor.OBJECT))) {
			superType = TypeDescriptor.ofClass("oshajava.runtime.ObjectWithState");
			superName = superType.getInternalName();
		}
		
		// Tell the class type about its superclass type.
		classType.setSuperType(superType);

		// Look up the module map for this class.
		try {
			moduleMap = Spec.getModuleMap(classType, loader);
		} catch (ModuleMapNotFoundException e) {
			if (Config.noSpecOption.get().test(classType.getSourceName()) != StringMatchResult.ACCEPT) {
				Assert.warn("No module map for class %s.  Using null module for all member methods.", classType);
			}
		}

		// TODO Fix frames so we can actually support Java 6 class file format in full.
		super.visit((version == Opcodes.V1_6 ? Opcodes.V1_5 : version), access, name, signature, superName, interfaces);
		{
			List<FieldDescriptor> superFields = null;
			try {
				superFields = FieldCollector.collect(superType, loader);
			} catch (IOException e) {
				Assert.fail(e);
			}

			for (FieldDescriptor fd : superFields) {
				FieldDescriptor myFd = FieldDescriptor.of(classType, fd.getFieldName(), fd.getFieldType(), fd.getAccessFlags());
				if (!Filter.shouldInstrument(superType) && Filter.shouldInstrument(myFd)) {
					addShadowField(myFd);
				}
			}
		}
		
//		// Get this class' superclass to find inherited fields that need to be shadowed.
//		Class<?> superclass = classForName(superType.getSourceName());
//		if (superclass == null) {
//			Assert.fail("superclass not found: " + Type.getObjectType(superName).getClassName());
//		}
//			
//		// Shadow any unshadowed inherited fields. Keep track of which fields we shadow here
//		// to avoid conflicts with fields declared here.
//		for (Field fld : getAllFields(superclass)) {
//			final FieldDescriptor fd = FieldDescriptor.of(superType, fld.getName(), TypeDescriptor.fromDescriptorString(Type.getDescriptor(fld.getType())));
//			if (InstrumentationAgent.shouldInstrument(fd) && !hasShadowField(superclass, name)) {
//				addShadowField(fld.getModifiers(), fd);
//				shadowedInheritedFields.add(fld.getName());
//			}
//		}
	}
	
	@Override
	public void visitOuterClass(String owner, String containingMethodName, String containingMethodDesc) {
//	    outerClassName = owner;
//		outerClassDesc = getDescriptor(owner);
		classType.setOuterType(TypeDescriptor.ofClass(owner), (classAccess & Opcodes.ACC_STATIC) == 0);
		super.visitOuterClass(owner, containingMethodName, containingMethodDesc);
	}
	
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		if (  (Config.objectTrackingOption.get() == Config.Granularity.FINE || (access & Opcodes.ACC_STATIC) != 0)) {
			// TODO option to ignore final fields. how?
			// We make all state fields non-final to be able to set them from outside a constructor
			addShadowField(FieldDescriptor.of(classType, name, TypeDescriptor.fromDescriptorString(desc), access));
		}
		return super.visitField(access, name, desc, signature, value);
	}

	private boolean visitedClinit = false;
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
	    MethodVisitor chain = super.visitMethod(access, name, desc, signature, exceptions);
		if ((access & Opcodes.ACC_NATIVE) == 0) {
		    if (!name.equals("<clinit>")) {
		    	if ((classAccess & Opcodes.ACC_INTERFACE) == 0) {
		    		// (Not instrumenting class initializers avoids balooning the size
		    		//  of large literal table constructions. It also makes sense, I
		    		//  think, because no "communication" should occur (semantically)
		    		//  from class loading.) TODO Revisit class initializers.
		    		ModuleSpec module;
		    		final MethodDescriptor method = MethodDescriptor.of(classType, name, desc, signature);
		    		if (moduleMap == null) {
			    		// if the module map is null, then we've already warned that this class will be dumped in the null module.
		    			if (Config.noSpecActionOption.get() == Config.DefaultSpec.UNTRACKED) {
		    				return chain;
		    			}
		    			module = NullModuleSpec.MODULE;
		    		} else {
		    			try {
		    				try {
		    					module = Spec.getModule(moduleMap.get(method), loader, method);
		    				} catch (MissingEntryException e) {
		    					if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
		    						// synthetic method.
			    					Assert.warn("SYNTHETIC method %s missing from module map for class %s (%s).  Using null module.", 
			    							method, classType.getInternalName(), classType);
		    					} else {
			    					// If a particular method is missing from the module map, we need to warn! (Maybe fail.)
		    						if (Config.noSpecOption.get().test(classType.getSourceName()) != StringMatchResult.ACCEPT) {
		    							Assert.warn("Method %s missing from module map for class %s (%s).  Using null module.", 
		    									method, method.getInternalName(), classType);
		    						}
		    					}
		    					module = NullModuleSpec.MODULE;
		    				}
		    			} catch (ModuleSpecNotFoundException e) {
		    				throw e.wrap();
		    			}
		    		}
		    		chain = new HandlerSorterAdapter(chain, access, name, desc, signature, exceptions);
		    		chain = new MethodInstrumentor(chain, access, name, desc, module, method);
		    		chain = new JSRInlinerAdapter(chain, access, name, desc, signature, exceptions);
		    	}
			} else if (name.equals("<clinit>") && (classAccess & Opcodes.ACC_INTERFACE) != 0) {
			    // Class initializer for an interface. Inline the initialization.
			    visitedClinit = true;
			    Debug.debugf("clinit", "instrumenting clinit in %s", classType);
			    return new StaticShadowInitInserter(chain, access, name, desc, classType.getAsmType(), staticShadowedFields);
		    } else { // Generate <clinit> for class
		    	visitedClinit = true;
		    	Debug.debugf("clinit", "instrumenting clinit in %s", classType);
		    	return new StaticShadowInitInserter(chain, access, name, desc, classType.getAsmType(), null);
		    }
		}
		return chain;
	}

	@Override
	public void visitEnd() {
		if ((classAccess & Opcodes.ACC_INTERFACE) == 0) {
			// instance.
			GeneratorAdapter instance = new GeneratorAdapter(Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC, INSTANCE_SHADOW_INIT_METHOD, 
					super.visitMethod(Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC, INSTANCE_SHADOW_INIT_METHOD.getName(), 
							INSTANCE_SHADOW_INIT_METHOD.getDescriptor(), null, null));
			instance.visitCode();

			// call super.initer()
			if (Filter.shouldInstrument(superType)) {
				instance.loadThis();
				instance.visitMethodInsn(Opcodes.INVOKESPECIAL, superType.getInternalName(), INSTANCE_SHADOW_INIT_METHOD.getName(), INSTANCE_SHADOW_INIT_METHOD.getDescriptor());
			}
			if (!instanceShadowedFields.isEmpty()) {
				int varCurrentState = instance.newLocal(STATE_TYPE);
				instance.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_CURRENT_STATE);
				instance.storeLocal(varCurrentState);

				for (FieldDescriptor field : instanceShadowedFields) {
				    // Shadow field.
					instance.loadThis();
					instance.loadLocal(varCurrentState);
					instance.visitFieldInsn(Opcodes.PUTFIELD, field.getDeclaringType().getInternalName(), field.getFieldName() + SHADOW_FIELD_SUFFIX, STATE_DESC);
					
					// Traceback field.
					if (Config.stackTracesOption.get()) {
					    instance.loadThis();
					    instance.push(0);
					    instance.newArray(STACKTRACE_TYPE.getElementType());
					    instance.visitFieldInsn(Opcodes.PUTFIELD, field.getDeclaringType().getInternalName(), field.getFieldName() + STACKTRACE_FIELD_SUFFIX, STACKTRACE_DESC);
					}
				}
				instance.visitInsn(Opcodes.RETURN);
				instance.visitMaxs(2, 1);
			} else {
				instance.visitInsn(Opcodes.RETURN);
				instance.visitMaxs(1, 0);
			}
			instance.visitEnd();

			if ((classAccess & Opcodes.ACC_INTERFACE) == 0 && (visitedClinit || !staticShadowedFields.isEmpty())) {
				// static.
				GeneratorAdapter stat = new GeneratorAdapter(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, STATIC_SHADOW_INIT_METHOD, 
						super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, STATIC_SHADOW_INIT_METHOD.getName(), STATIC_SHADOW_INIT_METHOD.getDescriptor(), null, null));
				stat.visitCode();
				if (!staticShadowedFields.isEmpty()) {
					int varCurrentState = stat.newLocal(STATE_TYPE);
					stat.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_CURRENT_STATE);
					stat.storeLocal(varCurrentState);
					
					for (FieldDescriptor field : staticShadowedFields) {
					    // Shadow field.
						stat.loadLocal(varCurrentState);
						stat.visitFieldInsn(Opcodes.PUTSTATIC, field.getDeclaringType().getInternalName(), field.getFieldName() + SHADOW_FIELD_SUFFIX, STATE_DESC);
						
						// Traceback field.
    					if (Config.stackTracesOption.get()) {
    					    stat.push(0);
    					    stat.newArray(STACKTRACE_TYPE.getElementType());
    					    stat.visitFieldInsn(Opcodes.PUTSTATIC, field.getDeclaringType().getInternalName(), field.getFieldName() + STACKTRACE_FIELD_SUFFIX, STACKTRACE_DESC);
    					}
					}
					stat.visitInsn(Opcodes.RETURN);
					stat.visitMaxs(1, 1);
				} else {
					stat.visitInsn(Opcodes.RETURN);
					stat.visitMaxs(0, 0);
				}
				stat.visitEnd();
			}
			if (!visitedClinit && !staticShadowedFields.isEmpty()) {
				final int acc = Opcodes.ACC_STATIC;
				final String name = "<clinit>";
				final String desc = "()V";
				GeneratorAdapter clinit = new GeneratorAdapter(
						new StaticShadowInitInserter(
								super.visitMethod(acc, name, desc, null, null),
								acc, name, desc, classType.getAsmType(), ((classAccess & Opcodes.ACC_INTERFACE) != 0 ? staticShadowedFields : null)),
								acc, name, desc
				);

				clinit.visitCode();
				clinit.visitInsn(Opcodes.RETURN);
				clinit.visitMaxs(0, 0);
				clinit.visitEnd();
			}
		}
	}

}
