package oshajava.instrument;


import oshajava.sourceinfo.BitVectorIntSet;
import oshajava.sourceinfo.MethodTable;
import oshajava.sourceinfo.UniversalIntSet;
import oshajava.support.acme.util.Util;
import oshajava.support.org.objectweb.asm.AnnotationVisitor;
import oshajava.support.org.objectweb.asm.Label;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.AdviceAdapter;

// TODO allow annotations on interface methods, applied to all their
// implementers?  This opens a bigger can of worms:
// TODO allow annotation inheritance?

public class MethodInstrumentor extends AdviceAdapter {

	protected static final int UNINITIALIZED = -1;
	protected int mid = UNINITIALIZED;
	protected final boolean isMain;
	protected final boolean isSynchronized;
	protected final boolean isConstructor;
	protected final boolean isClinit;
	protected final boolean isStatic;

	protected final String fullNameAndDesc;

	// TODO policy names: @Inline, @Private, @ShareSelf, @ShareProtected, @SharePublic
	// @Inline, @Private, @Self, @Group, @World
	// @Inline, @Private, @Self, @Some, @All
	// @Inline, @Private, @Self, @Shared, @Global
	public static enum Policy { INLINE, PRIVATE, PROTECTED, PUBLIC }

	// TODO make this a command line option, and a per-class option
	protected static final Policy POLICY_DEFAULT = Policy.INLINE;

	protected Policy policy = null;

	protected final ClassInstrumentor inst;

	protected int myMaxStackAdditions = 0;

	protected int originalMaxLocals = UNINITIALIZED, originalMaxStack = UNINITIALIZED;
	
	public MethodInstrumentor(MethodVisitor next, int access, String name, String desc, ClassInstrumentor inst) {
		super(next, access, name, desc);
		this.inst = inst;
		isStatic = (access & Opcodes.ACC_STATIC) != 0;
		isMain = (access & Opcodes.ACC_PUBLIC ) != 0 && isStatic
		&& name.equals("main") && desc.equals("([Ljava/lang/String;)V");
		isSynchronized = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
		isConstructor = name.equals("<init>");
		isClinit = name.equals("<clinit>");
		fullNameAndDesc = inst.className + "." + name + desc;
	}

	protected void myStackSize(int size) {
		if (size > myMaxStackAdditions) {
			myMaxStackAdditions = size;
		}
	}

	private Integer tempLocalAddress = null;
	private Integer tempLocalBoolean = null;
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
	private boolean threadVarInitialized = false, stateVarInitialized = false;
	
	protected void initializeThreadVar() {
		varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
		super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_THREAD_STATE);
		// sotre the current threadState into a local.
		super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
		threadVarInitialized = true;
	}

	private void initializeThreadVarFromEnterHook() {
		varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
		// sotre the current threadState into a local.
		super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
		threadVarInitialized = true;
	}

	protected void initializeStateVar() {
		if (policy != Policy.PUBLIC) {
			myStackSize(1);
			varCurrentState  = super.newLocal(ClassInstrumentor.STATE_TYPE);
			// stack -> threadstate
			pushCurrentThread();
			// stack -> state
			super.getField(ClassInstrumentor.THREAD_STATE_TYPE, ClassInstrumentor.CURRENT_STATE_FIELD, ClassInstrumentor.STATE_TYPE);
			// stack ->
			super.storeLocal(varCurrentState, ClassInstrumentor.STATE_TYPE);
			stateVarInitialized = true;
		}
	}

	@Override
	public void visitLineNumber(int line, Label start) {
		// TODO Auto-generated method stub
//		Util.logf("Line %d ------------------------------------", line);
		super.visitLineNumber(line, start);
	}

	/**
	 * Push the current threadstate onto the stack.
	 */
	protected void pushCurrentThread() {
		Util.assertTrue(threadVarInitialized);
//		initializeThreadVar();
		super.loadLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	}

	/**
	 * Push the current state onto the stack.
	 */
	protected void pushCurrentState() {
		// stack == 
		if (policy == Policy.PUBLIC) {
			// stack ->  null
			mv.visitInsn(Opcodes.ACONST_NULL);
		} else {
			Util.assertTrue(threadVarInitialized && stateVarInitialized);
			// stack -> state
			super.loadLocal(varCurrentState, ClassInstrumentor.STATE_TYPE);
		}
		// stack == state
	}
	
	/**
	 * If the top item on the stack is the threadstate of the current thread,
	 * jump to l.
	 * @param l
	 */
	protected void ifSameThread(Label l) {
		// stack == threadstate'
		// stack -> threadstate' threadstate
		pushCurrentThread();
		// stack ->
		super.ifCmp(ClassInstrumentor.THREAD_STATE_TYPE, EQ, l);
	}
	
	protected void ifSameState(Label l) {
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
	
	
	/**
	 * If the current state is not null, jump to l.
	 * @param l
	 */
//	protected void ifNullState(Label l) {}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		// Only one osha annotation allowed per method.
		Util.assertTrue(policy == null, "Only one policy allowed per method.");

		if (desc.equals(ClassInstrumentor.ANNOT_INLINE_DESC)) {
			policy = Policy.INLINE;
			return null;
		} else if (desc.equals(ClassInstrumentor.ANNOT_THREAD_PRIVATE_DESC)) {
			policy = Policy.PRIVATE;
			mid = MethodTable.register(fullNameAndDesc, null);
			return null;
		} else if (desc.equals(ClassInstrumentor.ANNOT_READ_BY_DESC)) {
			policy = Policy.PROTECTED;
			final BitVectorIntSet readerSet = new BitVectorIntSet();
			mid = MethodTable.register(fullNameAndDesc, readerSet);
			return new AnnotationRecorder(readerSet);
		} else if (desc.equals(ClassInstrumentor.ANNOT_READ_BY_ALL_DESC)) {
			policy = Policy.PUBLIC;
			mid = MethodTable.register(fullNameAndDesc, UniversalIntSet.set);
			//			Util.logf("%s (mid = %d) is ReadByAll. set in table = %s", fullNameAndDesc, mid, MethodRegistry.policyTable[mid]);
			return null;
		}  else {
			// Not one of ours.
			return super.visitAnnotation(desc, visible);
		}
	}

	class AnnotationRecorder implements AnnotationVisitor {
		protected final BitVectorIntSet readerSet;

		public AnnotationRecorder(BitVectorIntSet readerSet) {
			this.readerSet = readerSet;
		}

		public void visit(String name, Object value) {
			if (name == null) {
				MethodTable.requestID((String)value, readerSet);
			} else if (name.equals("value")) {
				Util.log("add " + name);
				for (String m : (String[])value) {
					MethodTable.requestID(m, readerSet);
				}
			}
		}

		public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
		public AnnotationVisitor visitArray(String name) {
			return name.equals("value") ? this : null;
		}
		public void visitEnd() { }
		public void visitEnum(String name, String desc, String value) { }
	}

	@Override
	public void visitCode() {
		if (policy == null) {
			if (isMain || isClinit) {
				policy = Policy.PUBLIC;
				mid = MethodTable.register(fullNameAndDesc, UniversalIntSet.set);
			} else {
				policy = POLICY_DEFAULT;
			}
			switch(policy) {
			case PUBLIC:
				mid = MethodTable.register(fullNameAndDesc, UniversalIntSet.set);
				break;
			case PROTECTED:
				Util.fail("not sure what to do here. think this is prohibited.");
				break;
			case PRIVATE:
				mid = MethodTable.register(fullNameAndDesc, null);
				break;
			case INLINE:
				break;
			}
		}
		super.visitCode();
		myStackSize(1);
		
		if (policy != Policy.INLINE) {
			super.push(mid);
			// stack -> threadstate
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ENTER);
			initializeThreadVarFromEnterHook();
		} else {
			initializeThreadVar();
		}
		initializeStateVar();
		// To init those ^^^ lazily, you need to know about control flow. We don't.
		
		if (isSynchronized) {
			myStackSize(3);
			if (isStatic) {
				// get class (lock). stack -> lock
				super.push(inst.classType);
			} else {
				// get object (lock). stack -> lock
				super.loadThis();
			}
			pushCurrentThread();
			pushCurrentState();
			// call acquire hook. stack ->
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ACQUIRE);

			// start try block.
			if (policy != Policy.INLINE || isSynchronized) {
				mv.visitTryCatchBlock(beginTry, endTryBeginHandler, endTryBeginHandler, null);
				super.mark(beginTry);
			}
		}
	}

	protected final Label beginTry = super.newLabel();
	protected final Label endTryBeginHandler = super.newLabel();

	@Override
	protected void onMethodEnter() {
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		originalMaxLocals = maxLocals;
		originalMaxStack = maxStack;
	}

	@Override
	public void visitEnd() {
		// If this is a non-abstract, non-interface, real, warm-blooded method.
		if (originalMaxStack != UNINITIALIZED) {
			// on ICEs, call release and exit hooks as needed and
			if (policy != Policy.INLINE || isSynchronized) {
				super.mark(endTryBeginHandler);
				makeReleaseExitHook(1);
				super.throwException();
			}

			// -- end code -------------
			super.visitMaxs(originalMaxStack + myMaxStackAdditions + 2, originalMaxLocals);
		}

		super.visitEnd();
	}

	protected void makeReleaseExitHook(int extraStack) {
		if (isSynchronized) {
			myStackSize(2 + extraStack);
			if (isStatic) {
				// get class (lock). stack -> lock
				super.push(inst.classType);
			} else {
				// get object (lock). stack -> lock
				super.loadThis();
			}
			pushCurrentThread();
			// call release hook. stack ->
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_RELEASE);
		}
		if (policy != Policy.INLINE) {
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_EXIT);
		}
	}

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {//TODO
	}

	/**
	 * Instrument accesses with read and write hooks.
	 */
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {

		if (inst.shouldInstrumentField(owner, name, desc)) {

			// TODO figure out how to add visitFrame where needed below (GOTOs) to
			// avoid cost of computing the frames...?
			//		Util.log("Visiting field ins: owner = " + owner + ", name = " + name + ", desc = " + desc);
			final Type ownerType = Type.getType(ClassInstrumentor.getDescriptor(owner));
			final String stateFieldName;
			if (inst.opts.coarseFieldStates && (opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETFIELD)){
				stateFieldName = ClassInstrumentor.SHADOW_FIELD_SUFFIX;
			} else {
				stateFieldName = name + ClassInstrumentor.SHADOW_FIELD_SUFFIX;
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
				// swap back (may push 2 past the bar temporarily). stack -> obj value |
				super.swap(fieldType,  ClassInstrumentor.OBJECT_TYPE);
				break;
			case Opcodes.PUTSTATIC:
				myStackSize(1);
				// get the current state. stack -> state
				pushCurrentState();
				// store the new state. stack -> 
				super.putStatic(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				break;
			case Opcodes.GETFIELD:
				myStackSize(2);
				// dup the target. stack -> obj | obj
				super.dup();
				// Get the State for this field. stack -> obj | state
				super.getField(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				// stack -> obj | state state
				super.dup();
				Label homeFree = super.newLabel();
//				//stack -> obj | state
//				ifSameState(homeFree); // OK if same state (same thread, same method, state maybe == null)
//				// stack -> obj | state state
//				super.dup();
				// stack -> obj | state
				super.ifNull(homeFree);
//				// stack -> obj | state state
//				super.dup();
//				// stack -> obj | state threadstate
//				loadThreadFromState();
//				// stack -> obj | state
//				ifSameThread(homeFree);
				// stack -> obj | state threadstate
				pushCurrentThread();
				// call the read hook. stack -> obj | 
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_READ);
				Label ok = super.newLabel();
				super.goTo(ok);
				super.mark(homeFree);
				super.pop();
				super.mark(ok);
				break;
			case Opcodes.GETSTATIC:
				myStackSize(2);
				// Get the State for this field. stack -> state
				super.getStatic(ownerType, stateFieldName, ClassInstrumentor.STATE_TYPE);
				super.dup();
				Label sHomeFree = super.newLabel();
				super.ifNull(sHomeFree);
				//stack -> state threadstate
				pushCurrentThread();
				// call the read hook. stack -> 
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_READ);
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


	private void xastore(int opcode, int local, int width) {
		// stack == array index value |
		if (inst.opts.coarseArrayStates) {
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
		} else {
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
		}

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
			if (inst.opts.coarseArrayStates) {
				myStackSize(2);
				// stack -> index array
				super.swap();
				// stack -> array index array
				super.dupX1();
				// stack -> array index array threadstate
				pushCurrentThread();
				// call the hook. stack -> array index
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_COARSE_ARRAY_LOAD);
			} else {
				myStackSize(3);
				// stack -> array index array index
				super.dup2();
				// stack -> array index array index threadstate
				pushCurrentThread();
				// stack -> array index _
				super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ARRAY_LOAD);
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
			myStackSize(1);
			// put in a try/finally to put in the release if needed...
			final Label start = super.newLabel(), handler = super.newLabel(), done = super.newLabel();
			mv.visitTryCatchBlock(start, handler, handler, ClassInstrumentor.OSHA_EXCEPT_TYPE_NAME);

			// dup the target. stack -> lock | lock
			super.dup();
			// save. stack -> lock |
			final int lock = super.newLocal(ClassInstrumentor.OBJECT_TYPE);
			super.storeLocal(lock);
			// dup. stack -> lock | lock
			super.dup();
			// do the monitorenter. stack -> lock |
			super.visitInsn(opcode);

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
			break;
		case Opcodes.MONITOREXIT:
			myStackSize(2);
			// dup the target. stack -> lock | lock
			super.dup();
			pushCurrentThread();
			// call release hook. stack -> lock |
			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_RELEASE);
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
		default:
			super.visitInsn(opcode);
		break;			
		}
	}

	//	@Override
	//	public void visitIntInsn(int opcode, int operand) {
	//		if (opcode == Opcodes.NEWARRAY) {
	//			myStackSize(1);
	//			// stack == length
	//			// stack -> length length
	//			super.dup();
	//			// stack -> length array
	//			super.visitIntInsn(opcode, operand);
	//			// stack -> array length array
	//			super.dupX1();
	//			// stack -> array
	//			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_NEW_ARRAY);
	//		} else {
	//			super.visitIntInsn(opcode, operand);
	//		}
	//	}

	//	@Override
	//	public void visitTypeInsn(int opcode, String type) {
	//		if (opcode == Opcodes.ANEWARRAY) {
	//			myStackSize(1);
	//			// stack == length
	//			// stack -> length length
	//			super.dup();
	//			// stack -> length array
	//			super.visitTypeInsn(opcode, type);
	//			// stack -> array length array
	//			super.dupX1();
	//			// stack -> array
	//			super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_NEW_ARRAY);
	//		} else {
	//			super.visitTypeInsn(opcode, type);
	//		}
	//	}

	//	@Override
	//	public void visitMultiANewArrayInsn(String desc, int dims) {
	//		// stack -> array
	//		super.visitMultiANewArrayInsn(desc, dims);
	//		// stack -> array array
	//		super.dup();
	//		// stack -> array array dims
	//		super.push(dims);
	//		// stack -> array
	//		super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_NEW_MULTI_ARRAY);
	//		
	//	}

	@Override
	public void visitVarInsn(int opcode, int var) {
		if (opcode == Opcodes.RET) {
			Util.fail("RET not supported.");
		} else {
			super.visitVarInsn(opcode, var);
		}
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		if (opcode == Opcodes.JSR) {
			Util.fail("JSR not supported.");
		} else {
			super.visitJumpInsn(opcode, label);
		}
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (isConstructor && inst.superName.equals(ClassInstrumentor.OBJECT_WITH_STATE_NAME) 
				&& opcode == Opcodes.INVOKESPECIAL && owner.equals("java/lang/Object") && name.equals("<init>")) {
			owner = ClassInstrumentor.OBJECT_WITH_STATE_NAME;
		}
		super.visitMethodInsn(opcode, owner, name, desc);
	}

}
