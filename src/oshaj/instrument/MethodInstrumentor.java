package oshaj.instrument;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import oshaj.util.BitVectorIntSet;
import oshaj.util.UniversalIntSet;
import acme.util.Util;

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

	protected Policy policy = Policy.INLINE;

	protected final Instrumentor inst;

	protected int myMaxStackAdditions = 0;

	protected int originalMaxLocals = UNINITIALIZED, originalMaxStack = UNINITIALIZED;

	public MethodInstrumentor(MethodVisitor next, int access, String name, String desc, Instrumentor inst) {
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

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		// TODO if isMain, don't inline! 
		if (desc.equals(Instrumentor.ANNOT_INLINE_DESC)) {
			policy = Policy.INLINE;
			return null;
		} else if (desc.equals(Instrumentor.ANNOT_THREAD_PRIVATE_DESC)) {
			policy = Policy.PRIVATE;
			mid = MethodRegistry.register(fullNameAndDesc, null);
			return null;
		} else if (desc.equals(Instrumentor.ANNOT_READ_BY_DESC)) {
			policy = Policy.PROTECTED;
			final BitVectorIntSet readerSet = new BitVectorIntSet();
			mid = MethodRegistry.register(fullNameAndDesc, readerSet);
			return new AnnotationRecorder(readerSet);
		} else if (desc.equals(Instrumentor.ANNOT_READ_BY_ALL_DESC)) {
			policy = Policy.PUBLIC;
			mid = MethodRegistry.register(fullNameAndDesc, UniversalIntSet.set);
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
				MethodRegistry.requestID((String)value, readerSet);
			} else if (name.equals("value")) {
				Util.log("add " + name);
				for (String m : (String[])value) {
					MethodRegistry.requestID(m, readerSet);
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
		if (mid == UNINITIALIZED && (isMain || isClinit)) {
			policy = Policy.PUBLIC;
			mid = MethodRegistry.register(fullNameAndDesc, UniversalIntSet.set);
		}
		super.visitCode();
	}
	
	protected final Label beginTry = super.newLabel();
	protected final Label endTryBeginHandler = super.newLabel();

	@Override
	protected void onMethodEnter() {
		if (policy != Policy.INLINE) {
			myStackSize(1);
			super.push(mid);
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ENTER);
		}
		if (isSynchronized) {
			myStackSize(2);
			if (isStatic) {
				// get class (lock). stack -> lock
				super.push(inst.classType);
			} else {
				// get object (lock). stack -> lock
				super.loadThis();
			}
			// get mid. stack -> lock mid
			switch (policy) {
			case INLINE:
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
				break;
			case PRIVATE:
			case PROTECTED:
			case PUBLIC:
				super.push(mid);
				break;
			}
			// call acquire hook. stack ->
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ACQUIRE);
			
			// start try block.
			if (policy != Policy.INLINE || isSynchronized) {
				mv.visitTryCatchBlock(beginTry, endTryBeginHandler, endTryBeginHandler, null);
				super.mark(beginTry);
			}
		}
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
			super.visitMaxs(originalMaxStack + myMaxStackAdditions, originalMaxLocals);
		}
		
		super.visitEnd();
	}
	
	protected void makeReleaseExitHook(int extraStack) {
		if (isSynchronized) {
			myStackSize(1 + extraStack);
			if (isStatic) {
				// get class (lock). stack -> lock
				super.push(inst.classType);
			} else {
				// get object (lock). stack -> lock
				super.loadThis();
			}
			// call release hook. stack ->
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_RELEASE);
		}
		if (policy != Policy.INLINE) {
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_EXIT);
		}
	}

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {//TODO
	}

	/**
	 * Instrument accesses with read and write hooks.
	 * 
	 * TODO handle anonymous classes correctly.	
	 */
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {

		if (inst.shouldInstrumentField(owner, name, desc)) {
			myStackSize(2);

			// TODO figure out how to add visitFrame where needed below (GOTOs) to
			// avoid cost of computing the frames...?
			//		Util.log("Visiting field ins: owner = " + owner + ", name = " + name + ", desc = " + desc);
			final Type ownerType = Type.getType(Instrumentor.getDescriptor(owner));
			final String stateFieldName = name + Instrumentor.SHADOW_FIELD_SUFFIX; 
			final Label nonNullState = new Label(), stateDone = new Label();

			switch(opcode) {
			case Opcodes.PUTFIELD:
				final Type fieldType = Type.getType(desc);				
				// stack == obj value |
				// swap (may push 2 past the bar temporarily). stack -> value obj |   
				super.swap(Instrumentor.OBJECT_TYPE, fieldType);
				// dup the target. stack -> value obj | obj
				super.dup();
				// Get the State for this field. stack -> value obj | state
				super.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> value obj | state state
				super.dup();
				// if non-null, jump ahead. stack -> value obj | state
				super.ifNonNull(nonNullState);

				// NULL CASE: first write
				// pop the null. stack -> value obj | 
				super.pop();
				// dup. stack -> value obj | obj
				super.dup();
				switch (policy) {
				case INLINE:
					// get the current method ID. stack -> value obj | obj mid
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// do a first write. stack -> value obj | obj state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> value obj | obj mid
					super.push(mid);
					// do a first write. stack -> value obj | obj state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> value obj | obj mid
					super.push(mid);
					// do a first write. stack -> value obj | obj state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_FIRST_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> value obj | obj mid
					super.push(mid);
					// do a first write. stack -> value obj | obj state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_FIRST_WRITE);
					break;
				}
				// stack == value obj | obj state
				// store the new state. stack -> value obj | 
				super.putField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// swap back (may push 2 past the bar temporarily). stack -> obj value |
				super.swap(fieldType,  Instrumentor.OBJECT_TYPE);
				// jump to end. stack -> obj value | 
				super.goTo(stateDone);

				// NON-NULL CASE: regular write
				// stack == value obj | state
				super.mark(nonNullState);
				switch (policy) {
				case INLINE:
					// Push the current method id. stack -> value obj | state mid
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// Call the write hook. stack -> value obj | 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> value obj | state mid
					super.push(mid);
					// Call the write hook. stack -> value obj | 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> value obj | state mid
					super.push(mid);
					// Call the write hook. stack -> value obj | 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> value obj | state mid
					super.push(mid);
					// Call the write hook. stack -> value obj | 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_WRITE);
					break;
				}
				// swap back (may push 2 past the bar temporarily). stack -> obj value |
				super.swap(fieldType,  Instrumentor.OBJECT_TYPE);
				super.mark(stateDone);

				break;
			case Opcodes.PUTSTATIC:
				// Get the State for this field. stack -> state
				super.getStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> state state
				super.dup();
				// if non-null, jump ahead. stack -> state
				super.ifNonNull(nonNullState);

				// NULL CASE: first write
				// pop the null. stack -> 
				super.pop();
				switch (policy) {
				case INLINE:
					// get the current method ID. stack -> mid
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// do a first write. stack -> state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> mid
					super.push(mid);
					// do a first write. stack -> state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> mid
					super.push(mid);
					// do a first write. stack -> state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_FIRST_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> mid
					super.push(mid);
					// do a first write. stack -> state
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_FIRST_WRITE);
					break;
				}
				// store the new state. stack -> 
				super.putStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// jump to end. stack -> 
				super.goTo(stateDone);

				// NON-NULL CASE: regular write
				// stack == state
				super.mark(nonNullState);
				switch (policy) {
				case INLINE:
					// Push the current method id. stack -> state mid
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// Call the write hook. stack -> 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> state mid
					super.push(mid);
					// Call the write hook. stack -> 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> state mid
					super.push(mid);
					// Call the write hook. stack -> 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> state mid
					super.push(mid);
					// Call the write hook. stack -> 
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_WRITE);
					break;
				}
				super.mark(stateDone);

				break;
			case Opcodes.GETFIELD:
				// dup the target. stack -> obj | obj
				super.dup();
				// Get the State for this field. stack -> obj | state
				super.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> obj | state state
				super.dup();
				// if non-null, jump ahead. stack -> obj | state
				super.ifNonNull(nonNullState);

				// NULL CASE: no communication
				// pop the null. stack -> obj | 
				super.pop();
				// jump to end. stack -> obj | 
				super.goTo(stateDone);

				// NON-NULL CASE: regular communication
				// stack == obj | state
				super.mark(nonNullState);
				// Push the current method id. stack -> obj | state mid
				switch (policy) {
				case INLINE:
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					break;
				case PRIVATE:
				case PROTECTED:
				case PUBLIC:
					super.push(mid);
					break;
				}
				// Call the read hook. stack -> obj | 
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_READ);
				super.mark(stateDone);

				break;
			case Opcodes.GETSTATIC:
				// Get the State for this field. stack -> state
				super.getStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> state state
				super.dup();
				// if non-null, jump ahead. stack -> state
				super.ifNonNull(nonNullState);

				// NULL CASE: no communication
				// pop the null. stack -> 
				super.pop();
				// jump to end. stack -> 
				super.goTo(stateDone);

				// NON-NULL CASE: regular communication
				// stack == state
				super.mark(nonNullState);
				// Push the current method id. stack -> state mid
				switch (policy) {
				case INLINE:
					super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					break;
				case PRIVATE:
				case PROTECTED:
				case PUBLIC:
					super.push(mid);
					break;
				}
				// Call the read hook. stack -> 
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_READ);
				super.mark(stateDone);

				break;
			}
			// END
		}

		// Do the actual op.
		super.visitFieldInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitInsn(int opcode) { 
		// TODO array load and store: {a,b,c,d,f,i,l,s}a{load,store}
		switch (opcode) {
		case Opcodes.MONITORENTER:
			myStackSize(2);
			// put in a try/finally to put in the release if needed...
			final Label start = super.newLabel(), handler = super.newLabel(), done = super.newLabel();
			mv.visitTryCatchBlock(start, handler, handler, Instrumentor.COMM_EXCEPT_TYPE_NAME);
			
			// dup the target. stack -> lock | lock
			super.dup();
			// save. stack -> lock
			final int lock = super.newLocal(Instrumentor.OBJECT_TYPE);
			super.storeLocal(lock);
			// dup. stack -> lock lock
			super.dup();
			// do the monitorenter. stack -> lock
			super.visitInsn(opcode);
			super.mark(start);
			// get mid. stack -> lock mid
			switch (policy) {
			case INLINE:
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
				break;
			case PRIVATE:
			case PROTECTED:
			case PUBLIC:
				super.push(mid);
				break;
			}
			// call acquire hook. stack -> 
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ACQUIRE);
			super.goTo(done);
			// handler. We get thee exception on the stack. stack -> e
			super.mark(handler);
			// reload lock. stack -> e lock
			super.loadLocal(lock);
			// monitorexit. stack -> e
			super.monitorExit();
			// rethrow. ->
			super.throwException();
			super.mark(done);
			break;
		case Opcodes.MONITOREXIT:
			myStackSize(1);
			// dup the target. stack -> lock | lock
			super.dup();
			// call release hook. stack -> lock |
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_RELEASE);
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
	public void visitMultiANewArrayInsn(String arg0, int arg1) {
		// TODO allocate shadow...?
		super.visitMultiANewArrayInsn(arg0, arg1);
	}

}
