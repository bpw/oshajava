package oshajava.instrument;


import oshajava.runtime.Config;
import oshajava.spec.CompiledModuleSpec;
import oshajava.spec.ModuleSpec;
import oshajava.spec.ModuleSpec.CommunicationKind;
import oshajava.spec.names.FieldDescriptor;
import oshajava.spec.names.MethodDescriptor;
import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.org.objectweb.asm.Label;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.AdviceAdapter;

public class MethodInstrumentor extends AdviceAdapter {
	
	private static final int UNINITIALIZED = -1;
	protected final boolean isMain;
	protected final boolean isSynchronized;
	protected final boolean isConstructor;
	protected final boolean isClinit;
	protected final boolean isStatic;
	protected final MethodDescriptor method;
	private int methodUID;
	
//	protected final int accessFlags;
	
	protected CommunicationKind policy;
	
	protected int myMaxStackAdditions = 0;

	protected int originalMaxLocals = UNINITIALIZED, originalMaxStack = UNINITIALIZED;
	
	public MethodInstrumentor(MethodVisitor next, int access, String name, String desc, ModuleSpec module, MethodDescriptor methodDescriptor) {
		super(next, access, name, desc);
//		this.accessFlags = access;
		this.method = methodDescriptor;
		this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
		this.isMain = (access & Opcodes.ACC_PUBLIC ) != 0 && isStatic && name.equals("main") && desc.equals("([Ljava/lang/String;)V");
		this.isSynchronized = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
		this.isConstructor = methodDescriptor.isConstructor();
		this.isClinit = methodDescriptor.isClassInit();
		final boolean isSynthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
				
		try { // FIXME  Centralize/unify handling of synthetics, missing methods, etc.
			methodUID = module.getMethodUID(methodDescriptor);
			// Set policy appropriately if method is found.
			policy = module.getCommunicationKind(methodUID);
		} catch (CompiledModuleSpec.MethodNotFoundException e) {
			methodUID = -1;
			if (isSynthetic) {
				// Synthetic methods are, in general, not seen by the annotation
				// processor. If they're not, then we inline the method.
				Assert.warn("Inlining synthetic method %s", methodDescriptor.getInternalName());
				policy = CommunicationKind.INLINE;
			} else if (methodDescriptor.getClassType().getInternalName().matches(".*\\$\\d.*")) {
				// Such is also the case with methods inside anonymous classes.
				Assert.warn("Anonymous class %s has method %s not in module %s. Inlining.", methodDescriptor.getClassType(), methodDescriptor, module.getName());
				policy = CommunicationKind.INLINE;
			} else {
				if (Agent.ignoreMissingMethodsOption.get()) {
					Assert.warn("IGNORED and INLINED: in module " + module.getName() + ", " + methodDescriptor + " not found");
				} else {
					Assert.fail("Method " + methodDescriptor + " not found in " + module);
				}
			}
		}
	}

	protected void myStackSize(int size) {
		if (size > myMaxStackAdditions) {
			myMaxStackAdditions = size;
		}
	}

	private Integer tempLocalAddress = null;
//	private Integer tempLocalBoolean = null;
	private Integer tempLocalByte = null;
	private Integer tempLocalChar = null;
	private Integer tempLocalDouble = null;
	private Integer tempLocalFloat = null;
	private Integer tempLocalInt = null;
	private Integer tempLocalLong = null;
	private Integer tempLocalShort = null;

	
	/**
	 * Local variables ids for storing the current threadstate and state.
	 */
	private int varCurrentThread;
	private int varCurrentState;
	private int varWriterCache;	
	
	/* Helper methods for inserting common bytecode sequences *******************************************/
	
	/**
	 * Lookup the current ThreadState from the RuntimeMonitor and store it int the
	 * varCurrentThread local variable.
	 */
	protected void initializeThreadVar() {
		varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
		// stack -> thread
		super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_THREAD_STATE);
		// stack ->
		super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	}

	/**
	 * Store a ThreadState on the top of the stack into the varCurrentThread local variable.
	 */
	private void initializeThreadVarFromStack() {
		varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
		// stack == thread
		// stack -> 
		super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	}

	protected void initializeStateAndCacheVars() {
		if (policy != CommunicationKind.UNCHECKED) {
			myStackSize(1);
			// LOAD STATE -----------------
			varCurrentState  = super.newLocal(ClassInstrumentor.STATE_TYPE);
			// stack -> threadstate
			pushCurrentThread();
			// stack -> state
			super.getField(ClassInstrumentor.THREAD_STATE_TYPE, ClassInstrumentor.CURRENT_STATE_FIELD, ClassInstrumentor.STATE_TYPE);
			// stack ->
			super.storeLocal(varCurrentState, ClassInstrumentor.STATE_TYPE);
			// LOAD WRITER CACHE -----------
			varWriterCache  = super.newLocal(ClassInstrumentor.BIT_VECTOR_INT_SET_TYPE);
			// stack -> state
			pushCurrentState();
			// stack -> stack
			super.getField(ClassInstrumentor.STATE_TYPE, ClassInstrumentor.STACK_FIELD, ClassInstrumentor.STACK_TYPE);
			// stack -> cache
			super.getField(ClassInstrumentor.STACK_TYPE, ClassInstrumentor.WRITER_CACHE_FIELD, ClassInstrumentor.BIT_VECTOR_INT_SET_TYPE);
			// stack ->
			super.storeLocal(varWriterCache, ClassInstrumentor.BIT_VECTOR_INT_SET_TYPE);
		}
	}

	/**
	 * Push the current threadstate onto the stack.
	 */
	protected void pushCurrentThread() {
		super.loadLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	}

	/**
	 * Push the current state onto the stack.
	 */
	protected void pushCurrentState() {
		// stack == 
		// stack -> state
		super.loadLocal(varCurrentState, ClassInstrumentor.STATE_TYPE);
		// stack == state
	}
	
	protected void pushWriterCache() {
		// stack == 
		// stack -> cache
		super.loadLocal(varWriterCache, ClassInstrumentor.BIT_VECTOR_INT_SET_TYPE);
		// stack == ache
	}

	/**
	 * If the top item on the stack is the threadstate of the current thread,
	 * jump to l.
	 * @param l
	 */
	protected void ifSameThreadGoto(Label l) {
		// stack == threadstate'
		// stack -> threadstate' threadstate
		pushCurrentThread();
		// stack ->
		super.ifCmp(ClassInstrumentor.THREAD_STATE_TYPE, EQ, l);
	}
	
	protected void ifSameStateGoto(Label l) {
		// stack == state'
		pushCurrentState();
		super.ifCmp(ClassInstrumentor.STATE_TYPE, EQ, l);
	}
	
	protected void loadThreadFromState() {
		// stack == state
		// stack -> threadstate
		super.getField(ClassInstrumentor.STATE_TYPE, ClassInstrumentor.THREAD_FIELD, ClassInstrumentor.THREAD_STATE_TYPE);
	}
	
	protected void ifCurrentStateNull(Label l) {
		pushCurrentState();
		super.ifNull(l);
	}
	

	// should be called with the lock on the stack
	private void acquireHook(int lock) {
    	// put in a try/finally to put in the release if needed...
    	final Label start = super.newLabel(), handler = super.newLabel(), done = super.newLabel();
    	mv.visitTryCatchBlock(start, handler, handler, ClassInstrumentor.OSHA_EXCEPT_TYPE_NAME);
	
	    super.mark(start);
		pushCurrentThread();
		pushCurrentState();
		// call acquire hook. stack ->
		super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ACQUIRE);

		super.goTo(done);

		// handler. We get thee exception on the stack. stack -> e |
		super.mark(handler);
		// reload lock. stack -> e | lock
		super.loadLocal(lock);
		// monitorexit. stack -> e |
		super.monitorExit();
		// rethrow. -> _ |
		super.throwException();
		super.mark(done);
	}
	
	// should be called with the lock on the stack
	private void releaseHook() {
		// stack == lock
		// stack -> lock thread
	    pushCurrentThread();
	    // stack -> 
		super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_RELEASE);
	}

	protected void makeReleaseExitHook(int extraStack) {
		if (isSynchronized && Config.lockTrackingOption.get()) {
			myStackSize(2 + extraStack);
			if (isStatic) {
				// get class (lock). stack -> lock
				super.push(method.getClassType().getInternalName());
			} else {
				// get object (lock). stack -> lock
				super.loadThis();
			}
			pushCurrentThread();
			// call release hook. stack ->
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_RELEASE);
		}
		if (policy != CommunicationKind.INLINE) {
			pushCurrentThread();
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_EXIT);
		}
	}

	private void xastore(int opcode, int local, int width) {
		// stack == array index value |
		switch (Config.arrayTrackingOption.get()) {
		case COARSE:
			myStackSize(3 - width);
			Label afterHook = super.newLabel();
			// stack -> array index _ |
			super.storeLocal(local);
			// stack -> index array _ |
			super.swap();
			// stack -> index array array |
			super.dup();
			// null check. stack -> index array _ |
			super.ifNull(afterHook);
			
			// NON-NULL CASE:
			// stack -> index array array |
			super.dup();
			// stack -> index array array | state
			pushCurrentState();
			pushCurrentThread();
			// call the hook. stack -> index array _ |
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COARSE_ARRAY_STORE);

			super.mark(afterHook);
			// BOTH CASES
			// stack -> array index _ |
			super.swap();
			// stack -> array index value |
			super.loadLocal(local);
			break;
		case FINE:
			myStackSize(4 - width);
			// stack -> array index _ |
			super.storeLocal(local);
			// stack -> array index array | index
			super.dup2();
			// stack -> array index array | index state threadstate
			pushCurrentState();
			pushCurrentThread();
			// stack -> array index _ |
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ARRAY_STORE);
			// stack -> array index value |
			super.loadLocal(local);
			break;
		}

	}

	// Get the current stack trace.
	private void getStackTrace() {
		// get the current Thread. stack -> thread
		super.invokeStatic(ClassInstrumentor.THREAD_TYPE, ClassInstrumentor.CURRENTTHREAD_METHOD);
		// get the stack trace. stack -> trace
		super.invokeVirtual(ClassInstrumentor.THREAD_TYPE, ClassInstrumentor.GETSTACKTRACE_METHOD);
    }

	/* Visitor methods ****************************************************************/

	protected final Label beginTry = super.newLabel();
	protected final Label endTryBeginHandler = super.newLabel();

	/**
	 * On non-inlined method entry, we load the current ThreadState and State and call
	 * the enter hook.  On synchronized method entry, we call the acquire hook. For both, we
	 * start an exception handler that will be used to make sure the release/exit hooks are
	 * called even if an exception is thrown.
	 */
	@Override
	public void visitCode() {
		super.visitCode();
		myStackSize(1);
		
		if (policy != CommunicationKind.INLINE) {
			super.push(methodUID);
			// stack -> threadstate
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ENTER);
			initializeThreadVarFromStack();
		} else {
			initializeThreadVar();
		}
		initializeStateAndCacheVars();
		// To init those ^^^ lazily, you need to know about control flow. We don't.
		
		if (isSynchronized && Config.lockTrackingOption.get()) {
			myStackSize(3);
			if (isStatic) {
				// get class (lock). stack -> lock
				super.push(method.getClassType().getInternalName());
			} else {
				// get object (lock). stack -> lock
				super.loadThis();
			}
			pushCurrentThread();
			pushCurrentState();
			// call acquire hook. stack ->
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ACQUIRE);
		}
		// start try block. This block is used to catch any exception and call the
		// exit hook for non-inlined methods and the release hook for syncrhonized methods.
		// Even though we've hit an error condition, we still want to preserve instrumentation that is
		// in sync with reality because a programmer could catch our exception and continue.
		if (policy != CommunicationKind.INLINE || (isSynchronized && Config.lockTrackingOption.get())) {
			mv.visitTryCatchBlock(beginTry, endTryBeginHandler, endTryBeginHandler, null);
			super.mark(beginTry);
		}
	}

	@Override
	public void visitEnd() {
		// If this is a non-abstract, non-interface, real, warm-blooded method.
		if (originalMaxStack != UNINITIALIZED) {
			// on ICEs, call release and exit hooks as needed 
			if (policy != CommunicationKind.INLINE || (isSynchronized && Config.lockTrackingOption.get())) {
				super.mark(endTryBeginHandler);
				makeReleaseExitHook(1);
				super.throwException();
			}

			// -- end code -------------
			super.visitMaxs(originalMaxStack + myMaxStackAdditions + 2, originalMaxLocals);
		}

		super.visitEnd();
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		originalMaxLocals = maxLocals;
		originalMaxStack = maxStack;
	}

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {//TODO implement frames correctly
	}
	
	/**
	 * Instrument accesses with read and write hooks.
	 */
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		FieldDescriptor field = FieldDescriptor.of(TypeDescriptor.ofClass(owner), name, TypeDescriptor.fromDescriptorString(desc));
		if (Filter.shouldInstrument(field)) {

			// TODO figure out how to add visitFrame where needed below (GOTOs) to
			// avoid cost of computing the frames...?
			//		Util.log("Visiting field ins: owner = " + owner + ", name = " + name + ", desc = " + desc);
			final Type ownerType = Type.getType(ClassInstrumentor.getDescriptor(owner));
			final String stateFieldName;
            final String stacktraceFieldName;
			if (Config.objectTrackingOption.get() == Config.Granularity.COARSE && (opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETFIELD)){
				stateFieldName = ClassInstrumentor.SHADOW_FIELD_SUFFIX;
				stacktraceFieldName = ClassInstrumentor.STACKTRACE_FIELD_SUFFIX;
			} else {
				stateFieldName = name + ClassInstrumentor.SHADOW_FIELD_SUFFIX;
				stacktraceFieldName = name + ClassInstrumentor.STACKTRACE_FIELD_SUFFIX;
			}
			switch(opcode) {
			case Opcodes.PUTFIELD:
				myStackSize(2);
				final Type fieldType = Type.getType(desc);				
				// stack == obj value |
				// swap (may push 2 past the bar temporarily). stack -> value obj |   
				super.swap(ClassInstrumentor.OBJECT_TYPE, fieldType);
				// dup the target. stack -> value obj | obj
				super.dup();
				
				// push the current state on the stack. stack -> value obj | obj state
				pushCurrentState();
				// store the new state. stack -> value obj | 
				super.putField(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				
				// Save stack trace if requested.
				if (Config.stackTracesOption.get()) {
			        // dup the target. stack -> value obj | obj
    				super.dup();
    				// get the stack trace. stack -> value obj | obj trace
    				getStackTrace();
            		// store the stack trace. stack -> value obj |
            		super.putField(ownerType, stacktraceFieldName, ClassInstrumentor.STACKTRACE_TYPE);
    			}
				
				// swap back (may push 2 past the bar temporarily). stack -> obj value |
				super.swap(fieldType,  ClassInstrumentor.OBJECT_TYPE);
				break;
			case Opcodes.PUTSTATIC:
				myStackSize(1);
				
				// get the current state. stack -> state
				pushCurrentState();
				// store the new state. stack -> 
				super.putStatic(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				
				// Save stack trace if requested.
				if (Config.stackTracesOption.get()) {
				    // get the stack trace. stack -> trace
				    getStackTrace();
            		// store the stack trace. stack -> 
            		super.putStatic(ownerType, stacktraceFieldName, ClassInstrumentor.STACKTRACE_TYPE);
    			}
				
				break;
			case Opcodes.GETFIELD:
				myStackSize(4);
				Label homeFree = super.newLabel();
				int traceVar = UNINITIALIZED;
				// Store the stack trace if requested.
				if (Config.stackTracesOption.get()) {
				    traceVar = super.newLocal(ClassInstrumentor.STACKTRACE_TYPE);
				    // stack -> obj | obj
				    super.dup();
				    // stack -> obj | trace
				    super.getField(ownerType, stacktraceFieldName, ClassInstrumentor.STACKTRACE_TYPE);
				    // stack -> obj |
				    super.storeLocal(traceVar);
			    }
				// if profiling, count the read! SLOOOOOOW
				if (Config.profileOption.get() == Config.ProfileLevel.DEEP) {
					super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COUNT_READ);
				}
				// dup the target. stack -> obj | obj
				super.dup();
				// Get the State for this field. stack -> obj | state
				super.getField(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				
				// IF INTER-THREAD ONLY -----------
				if (!Config.intraThreadOption.get()) {
					// stack -> obj | state state
					super.dup();
					// stack -> obj | state writerThread
					loadThreadFromState();
					// stack -> obj | state writerThread currentThread -> obj | state
					ifSameThreadGoto(homeFree); // FAST PATH if same thread we're done, else check stacks
				}
				// END IF INTER-THREAD ONLY -------
				
				// Fairly Fast Path
				
				// if profiling, count the communication! SLOW
				if (Config.profileOption.get() == Config.ProfileLevel.DEEP) {
					super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COUNT_COMM);
				}
				// stack -> obj | state state    <------ TODO maybe don't dup and just reload from field later if needed.
				super.dup();
				// stack -> obj | state state cache
				pushWriterCache();
				// stack -> obj | state cache state
				super.swap();
				// stack -> obj | state cache stackID
				super.invokeVirtual(ClassInstrumentor.STATE_TYPE, ClassInstrumentor.STATE_STACK_ID);
				// stack -> obj | state boolean
				super.invokeVirtual(ClassInstrumentor.BIT_VECTOR_INT_SET_TYPE, ClassInstrumentor.CONTAINS_METHOD);
				// stack -> obj | state
				super.ifZCmp(NE, homeFree); // if that succeeded, we're done, else do heavier check.
				
				// End Fairly Fast Path
				
				// SLOW PATH
				
				// stack -> obj | state readerState
				pushCurrentState();
				// push field name. stack -> obj | state readerState [trace] name
				super.push(field.getSourceName());
				if (Config.stackTracesOption.get()) {
					// stack -> obj | state readerState trace
				    super.loadLocal(traceVar);
				}
				// call the read hook. stack -> obj | 
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, (Config.stackTracesOption.get() ? 
						ClassInstrumentor.HOOK_READ_STACK_TRACE : ClassInstrumentor.HOOK_READ));
				
				// END SLOW PATH
				
				Label ok = super.newLabel();
				super.goTo(ok);
				super.mark(homeFree);
				super.pop();
				super.mark(ok);
				break;
			case Opcodes.GETSTATIC:
				myStackSize(3);
				Label sHomeFree = super.newLabel();
				// if profiling, count the read! SLOOOOOOW
				if (Config.profileOption.get() == Config.ProfileLevel.DEEP) {
					super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COUNT_READ);
				}
				// Get the State for this field. stack -> state
				super.getStatic(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				
				
				// IF INTER-THREAD ONLY -----------
				if (!Config.intraThreadOption.get()) {
					// stack -> state state
					super.dup();
					// stack -> state writerThread
					loadThreadFromState();
					// stack -> state writerThread currentThread -> state
					ifSameThreadGoto(sHomeFree); // FAST PATH if same thread we're done, else check stacks
				}
				// END IF INTER-THREAD ONLY -------
				
				// Fairly Fast Path
				
				// if profiling, count the communication! SLOW
				if (Config.profileOption.get() == Config.ProfileLevel.DEEP) {
					super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COUNT_COMM);
				}
				// stack -> state state    <------ TODO maybe don't dup and just reload from field later if needed.
				super.dup();
				// stack -> state state cache
				pushWriterCache();
				// stack -> state cache state
				super.swap();
				// stack -> state cache stackID
				super.invokeVirtual(ClassInstrumentor.STATE_TYPE, ClassInstrumentor.STATE_STACK_ID);
				// stack -> state boolean
				super.invokeVirtual(ClassInstrumentor.BIT_VECTOR_INT_SET_TYPE, ClassInstrumentor.CONTAINS_METHOD);
				// stack -> state
				super.ifZCmp(NE, sHomeFree); // if that succeeded, we're done, else do heavier check.
				
				// End Fairly Fast Path
				
				// SLOW PATH
				
				// stack -> state readerState
				pushCurrentState();
				// push field name. stack -> obj | state readerState [trace] name
				super.push(field.getSourceName());
				if (Config.stackTracesOption.get()) {
					// stack -> obj | state readerState trace
				    super.getStatic(ownerType, stacktraceFieldName, ClassInstrumentor.STACKTRACE_TYPE);
				}
				// call the read hook. stack -> 
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, (Config.stackTracesOption.get() ? 
						ClassInstrumentor.HOOK_READ_STACK_TRACE : ClassInstrumentor.HOOK_READ));
				
				// END SLOW PATH
				
				Label sEnd = super.newLabel();
				super.goTo(sEnd);
				super.mark(sHomeFree);
				super.pop();
				super.mark(sEnd);
				break;
			}
			// END
		}

		// Do the actual op.
		super.visitFieldInsn(opcode, owner, name, desc);
	}


	@Override
	public void visitInsn(int opcode) { 
		switch (opcode) {
		case Opcodes.AALOAD:
		case Opcodes.BALOAD:
		case Opcodes.CALOAD:
		case Opcodes.DALOAD:
		case Opcodes.FALOAD:
		case Opcodes.IALOAD:
		case Opcodes.LALOAD:			
		case Opcodes.SALOAD:
			switch (Config.arrayTrackingOption.get()) {
			case COARSE:
				myStackSize(3);
				// stack -> index array
				super.swap();
				// stack -> array index array
				super.dupX1();
				// stack -> array index array threadstate
				pushCurrentThread();
				// stack -> array index array threadstate cache
				pushWriterCache();
				// call the hook. stack -> array index
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COARSE_ARRAY_LOAD);
				break;
			case FINE:
				myStackSize(4);
				// stack -> array index array index
				super.dup2();
				// stack -> array index array index threadstate
				pushCurrentThread();
				// stack -> array index array index threadstate cache
				pushWriterCache();
				// stack -> array index _
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ARRAY_LOAD);
				break;
			}
			super.visitInsn(opcode);
			break;
		case Opcodes.AASTORE:
			if (tempLocalAddress == null) tempLocalAddress = super.newLocal(ClassInstrumentor.OBJECT_TYPE);
			xastore(opcode, tempLocalAddress, 1);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.BASTORE:
			if (tempLocalByte == null) tempLocalByte = super.newLocal(Type.BYTE_TYPE);
			xastore(opcode, tempLocalByte, 1);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.CASTORE:
			if (tempLocalChar == null) tempLocalChar = super.newLocal(Type.CHAR_TYPE);
			xastore(opcode, tempLocalChar, 1);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.FASTORE:
			if (tempLocalFloat == null) tempLocalFloat = super.newLocal(Type.FLOAT_TYPE);
			xastore(opcode, tempLocalFloat, 1);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.IASTORE:
			if (tempLocalInt == null) tempLocalInt = super.newLocal(Type.INT_TYPE);
			xastore(opcode, tempLocalInt, 1);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.SASTORE:
			if (tempLocalShort == null) tempLocalShort = super.newLocal(Type.SHORT_TYPE);
			xastore(opcode, tempLocalShort, 1);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.DASTORE:
			if (tempLocalDouble == null) tempLocalDouble = super.newLocal(Type.DOUBLE_TYPE);
			xastore(opcode, tempLocalDouble, 2);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.LASTORE:
			if (tempLocalLong == null) tempLocalLong = super.newLocal(Type.LONG_TYPE);
			xastore(opcode, tempLocalLong, 2);
			// actual store
			super.visitInsn(opcode);
			break;
		case Opcodes.MONITORENTER:
			if (!Config.lockTrackingOption.get()) {
				super.visitInsn(opcode);
				break;
			}
			myStackSize(1);

			// dup the target. stack -> lock | lock
			super.dup();
			// save. stack -> lock |
			final int lock = super.newLocal(ClassInstrumentor.OBJECT_TYPE);
			super.storeLocal(lock);
			// dup. stack -> lock | lock
			super.dup();
			// do the monitorenter. stack -> lock |
			super.visitInsn(opcode);

            acquireHook(lock);
			
			break;
		case Opcodes.MONITOREXIT:
			if (!Config.lockTrackingOption.get()) {
				super.visitInsn(opcode);
				break;
			}
			myStackSize(2);
			
			super.dup();
			releaseHook();
			
			super.visitInsn(opcode);
			break;
		case Opcodes.IRETURN:
		case Opcodes.LRETURN:
		case Opcodes.FRETURN:
		case Opcodes.DRETURN:
		case Opcodes.ARETURN:
		case Opcodes.RETURN:
			makeReleaseExitHook(0);
			super.visitInsn(opcode);
			break;
		default:
			super.visitInsn(opcode);
		break;			
		}
	}
	
	@Override
	public void visitVarInsn(int opcode, int var) {
		if (opcode == Opcodes.RET) {
			Assert.fail("RET not supported.");
		} else {
			super.visitVarInsn(opcode, var);
		}
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		if (opcode == Opcodes.JSR) {
			Assert.fail("JSR not supported.");
		} else {
			super.visitJumpInsn(opcode, label);
		}
	}

    private boolean calledOtherConstructor = false;
    private boolean methodEntered = false;
	@Override
	public void onMethodEnter() {
		if (isConstructor && !calledOtherConstructor) {
			// No call to this() or super() (i.e., this is a root class like Object).
			myStackSize(1);
			super.loadThis();
			super.invokeVirtual(method.getClassType().getAsmType(), ClassInstrumentor.INSTANCE_SHADOW_INIT_METHOD);
		}
		methodEntered = true;
	}
	
	private static final MethodDescriptor WAIT = MethodDescriptor.of(TypeDescriptor.OBJECT, "wait", "()V", null),
		WAIT_MILLIS = MethodDescriptor.of(TypeDescriptor.OBJECT, "wait", "(J)V", null),
		WAIT_MILLIS_NANOS = MethodDescriptor.of(TypeDescriptor.OBJECT, "wait", "(JI)V", null);

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		MethodDescriptor invokedMethod = MethodDescriptor.of(TypeDescriptor.ofClass(owner), name, desc, null);
	    if (isConstructor && !methodEntered && opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
			// Call to another constructor at the beginning of this constructor (i.e., this is not Object).
			calledOtherConstructor = true;
			
			// Patch calls to super/this constructors that should not be or is not instrumented.
			if (!Filter.shouldInstrument(invokedMethod)) { 
				// First, invoke the uninstrumented constructor.
    		    super.visitMethodInsn(opcode, invokedMethod.getClassType().getInternalName(), name, desc);
    		    // Then initialize the shadow fields.
    			myStackSize(1);
    			super.loadThis();
    			super.invokeVirtual(method.getClassType().getAsmType(), ClassInstrumentor.INSTANCE_SHADOW_INIT_METHOD);
    			return;
			}
		}
	    	    
		if (isConstructor && method.getClassType().getSuperType().equals(ClassInstrumentor.OBJECT_WITH_STATE) 
				&& opcode == Opcodes.INVOKESPECIAL && invokedMethod.getClassType().equals(TypeDescriptor.OBJECT) && name.equals("<init>")) {
			// Shim in object-granularity tracking.
		    super.visitMethodInsn(opcode, ClassInstrumentor.OBJECT_WITH_STATE.getInternalName(), name, desc);
		    return;
		} else if (Config.lockTrackingOption.get() && opcode == Opcodes.INVOKEVIRTUAL && invokedMethod.getClassType().equals(TypeDescriptor.OBJECT) && name.equals("wait")) {
		    myStackSize(3);
		    
		    // There are three forms of wait(). Put the arguments aside.
		    final int longArg = super.newLocal(Type.LONG_TYPE);
		    final int intArg = super.newLocal(Type.INT_TYPE);
		    if (invokedMethod.getReturnType().equals(MethodInstrumentor.WAIT)) {
		        // No arguments. Do nothing.
		    } else if (invokedMethod.getReturnType().equals(MethodInstrumentor.WAIT_MILLIS)) {
		        super.storeLocal(longArg);
		    } else if (invokedMethod.getReturnType().equals(MethodInstrumentor.WAIT_MILLIS_NANOS)) {
		        super.storeLocal(intArg);
		        super.storeLocal(longArg);
		    } else {
		    	Assert.fail("wait() call with unknown descriptor");
		    }
		    
		    // stack -> lock lock
		    super.dup();
		    // stack -> lock lock lock
		    super.dup();
		    // stack -> lock lock lock thread
		    pushCurrentThread();
		    // stack -> lock lock depth
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_PREWAIT);
			// stack -> lock depth lock
			super.swap();
			
		    
		    // Put the arguments back. stack -> lock depth lock ...
		    if (desc.equals("(J)V")) {
		        super.loadLocal(longArg);
		    } else if (desc.equals("(JI)V")) {    
		        super.loadLocal(longArg);
		        super.loadLocal(intArg);
		    }
		    
		    // Invoke wait(). stack -> lock depth
		    super.visitMethodInsn(opcode, invokedMethod.getClassType().getInternalName(), name, desc);
		    // stack -> lock depth thread
		    pushCurrentThread();
		    // stack -> lock depth thread state
		    pushCurrentState();
		    // stack -> 
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_POSTWAIT);
			return;
		} else {
		    super.visitMethodInsn(opcode, invokedMethod.getClassType().getInternalName(), name, desc);
		    return;
        }
	}

}
