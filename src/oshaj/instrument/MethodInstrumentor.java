package oshaj.instrument;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;

import oshaj.util.BitVectorIntSet;
import oshaj.util.UniversalIntSet;


public class MethodInstrumentor extends AdviceAdapter {

	protected final GeneratorAdapter gen;

	protected int mid;
	protected final boolean isMain;
	protected final boolean isSynchronized;
	protected final boolean isConstructor;
	//	protected final boolean isClinit;
	protected final boolean isStatic;

	protected final String nameAndDesc;

	public static enum Policy { INLINE, PRIVATE, PROTECTED, PUBLIC }

	protected Policy policy = Policy.INLINE;

	protected final Instrumentor inst;

	protected int myMaxStackAdditions = 0;

	protected int lastFrameLocals, lastFrameStack;
	protected Object[] lastFrameLocalTypes = {}, lastFrameStackTypes = {};

	protected int originalMaxLocals, originalMaxStack;

	public MethodInstrumentor(MethodVisitor next, int access, String name, String desc, Instrumentor inst) {
		super(next, access, name, desc);
		this.inst = inst;
		isStatic = (access & Opcodes.ACC_STATIC) != 0;
		isMain = (access & Opcodes.ACC_PUBLIC ) != 0 && isStatic
		&& name.equals("main") && desc.equals("([Ljava/lang/String;)V");
		isSynchronized = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
		isConstructor = name.equals("\"<init>\"");
		//		isClinit = name.equals("\"<clinit>\"");
		nameAndDesc = name + desc;
		if (isMain) { // and it wasn't annotated...
			policy = Policy.PUBLIC;
			mid = MethodRegistry.register(nameAndDesc, UniversalIntSet.set);
		}
		gen = new GeneratorAdapter(next, access, name, desc);
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
			mid = MethodRegistry.register(nameAndDesc, null);
			return null;
		} else if (desc.equals(Instrumentor.ANNOT_READ_BY_DESC)) {
			policy = Policy.PROTECTED;
			final BitVectorIntSet readerSet = new BitVectorIntSet();
			mid = MethodRegistry.register(nameAndDesc, readerSet);
			return new AnnotationRecorder(readerSet);
		} else if (desc.equals(Instrumentor.ANNOT_READ_BY_ALL_DESC)) {
			policy = Policy.PUBLIC;
			mid = MethodRegistry.register(nameAndDesc, UniversalIntSet.set);
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
			if (name.equals("value")) {
				for (String m : (String[])value) {
					MethodRegistry.requestID(m, readerSet);
				}
			}
		}

		public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
		public AnnotationVisitor visitArray(String name) {
			throw new RuntimeException("AnnotationRecorder.visitArray called, but unimplemented");
		}
		public void visitEnd() { }
		public void visitEnum(String name, String desc, String value) { }
	}

	@Override
	public void onMethodEnter() {
		if (policy != Policy.INLINE) {
			myStackSize(1);
			gen.push(mid);
			gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ENTER);
		}
		if (isSynchronized) {
			myStackSize(2);
			if (isStatic) {
				// get class (lock). stack -> lock
				gen.push(inst.classType);
			} else {
				// get object (lock). stack -> lock
				gen.loadThis();
			}
			// get mid. stack -> lock mid
			switch (policy) {
			case INLINE:
				gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
				break;
			case PRIVATE:
			case PROTECTED:
			case PUBLIC:
				gen.push(mid);
				break;
			}
			// call acquire hook. stack ->
			gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ACQUIRE);
		}
	}

	@Override
	public void onMethodExit(int _) {
		if (isSynchronized) {
			myStackSize(1);
			if (isStatic) {
				// get class (lock). stack -> lock
				gen.push(inst.classType);
			} else {
				// get object (lock). stack -> lock
				gen.loadThis();
			}
			// call release hook. stack ->
			gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_RELEASE);
		}
		if (policy != Policy.INLINE) {
			gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_EXIT);
		}
	}

	@Override
	public void visitEnd() {
		super.visitMaxs(originalMaxStack + myMaxStackAdditions, originalMaxLocals);
		super.visitEnd();
	}

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {//TODO
	}

	/**
	 * Instrument accesses with read and write hooks.
	 * 
	 * Puts one int, one long, and one address on the stack.	
	 */
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {

		if (Instrumentor.shouldInstrument(owner)) {
			myStackSize(2);

			// TODO figure out how to add visitFrame where needed below (GOTOs) to
			// avoid cost of computing the frames...?
			//		Util.log("Visiting field ins: owner = " + owner + ", name = " + name + ", desc = " + desc);
			final Type ownerType = Type.getType(Instrumentor.getDescriptor(owner));
			final String stateFieldName = name + Instrumentor.SHADOW_FIELD_SUFFIX; 
			final Label nonNullState = new Label(), stateDone = new Label();

			switch(opcode) {
			case Opcodes.PUTFIELD:
				// stack == obj value |
				// swap. stack -> value obj |
				gen.swap();
				// dup the target twice. stack -> value obj | obj
				gen.dup();
				// Get the State for this field. stack -> value obj | state
				gen.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> value obj | state state
				gen.dup();
				// if non-null, jump ahead. stack -> value obj | state
				gen.ifNonNull(nonNullState);

				// NULL CASE: first write
				// pop the null. stack -> value obj | 
				gen.pop();
				// dup. stack -> value obj | obj
				gen.dup();
				switch (policy) {
				case INLINE:
					// get the current method ID. stack -> value obj | obj mid
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// do a first write. stack -> value obj | obj state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> value obj | obj mid
					gen.push(mid);
					// do a first write. stack -> value obj | obj state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> value obj | obj mid
					gen.push(mid);
					// do a first write. stack -> value obj | obj state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_FIRST_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> value obj | obj mid
					gen.push(mid);
					// do a first write. stack -> value obj | obj state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_FIRST_WRITE);
					break;
				}
				// store the new state. stack -> value obj | 
				gen.putField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// swap back. stack -> obj value |
				gen.swap();
				// jump to end. stack -> obj value | 
				gen.goTo(stateDone);

				// NON-NULL CASE: regular write
				// stack == value obj | state
				gen.mark(nonNullState);
				switch (policy) {
				case INLINE:
					// Push the current method id. stack -> value obj | state mid
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// Call the write hook. stack -> value obj | 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> value obj | state mid
					gen.push(mid);
					// Call the write hook. stack -> value obj | 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> value obj | state mid
					gen.push(mid);
					// Call the write hook. stack -> value obj | 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> value obj | state mid
					gen.push(mid);
					// Call the write hook. stack -> value obj | 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_WRITE);
					break;
				}
				// swap back. stack -> obj value |
				gen.swap();
				break;
			case Opcodes.PUTSTATIC:
				// Get the State for this field. stack -> state
				gen.getStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> state state
				gen.dup();
				// if non-null, jump ahead. stack -> state
				gen.ifNonNull(nonNullState);

				// NULL CASE: first write
				// pop the null. stack -> 
				gen.pop();
				switch (policy) {
				case INLINE:
					// get the current method ID. stack -> mid
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// load the readerset. stack -> mid set
					// do a first write. stack -> state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> mid
					gen.push(mid);
					// do a first write. stack -> state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> mid
					gen.push(mid);
					// do a first write. stack -> state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_FIRST_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> mid
					gen.push(mid);
					// do a first write. stack -> state
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_FIRST_WRITE);
					break;
				}
				// store the new state. stack -> 
				gen.putStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// jump to end. stack -> 
				gen.goTo(stateDone);

				// NON-NULL CASE: regular write
				// stack == state
				gen.mark(nonNullState);
				switch (policy) {
				case INLINE:
					// Push the current method id. stack -> state mid
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					// Call the write hook. stack -> 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PROTECTED:
					// Push the current method id. stack -> state mid
					gen.push(mid);
					// Call the write hook. stack -> 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
					break;
				case PUBLIC:
					// Push the current method id. stack -> state mid
					gen.push(mid);
					// Call the write hook. stack -> 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_WRITE);
					break;
				case PRIVATE:
					// Push the current method id. stack -> state mid
					gen.push(mid);
					// Call the write hook. stack -> 
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_WRITE);
					break;
				}

				break;
			case Opcodes.GETFIELD:
				// dup the target. stack -> obj | obj
				gen.dup();
				// Get the State for this field. stack -> obj | state
				gen.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> obj | state state
				gen.dup();
				// if non-null, jump ahead. stack -> obj | state
				gen.ifNonNull(nonNullState);

				// NULL CASE: no communication
				// pop the null. stack -> obj | 
				gen.pop();
				// jump to end. stack -> obj | 
				gen.goTo(stateDone);

				// NON-NULL CASE: regular communication
				// stack == obj | state
				gen.mark(nonNullState);
				// Push the current method id. stack -> obj | state mid
				switch (policy) {
				case INLINE:
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					break;
				case PRIVATE:
				case PROTECTED:
				case PUBLIC:
					gen.push(mid);
					break;
				}
				// Call the read hook. stack -> obj | 
				gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_READ);

				break;
			case Opcodes.GETSTATIC:
				// Get the State for this field. stack -> state
				gen.getStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
				// dup the state. stack -> state state
				gen.dup();
				// if non-null, jump ahead. stack -> state
				gen.ifNonNull(nonNullState);

				// NULL CASE: no communication
				// pop the null. stack -> 
				gen.pop();
				// jump to end. stack -> 
				gen.goTo(stateDone);

				// NON-NULL CASE: regular communication
				// stack == state
				gen.mark(nonNullState);
				// Push the current method id. stack -> state mid
				switch (policy) {
				case INLINE:
					gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
					break;
				case PRIVATE:
				case PROTECTED:
				case PUBLIC:
					gen.push(mid);
					break;
				}
				// Call the read hook. stack -> 
				gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_READ);

				break;
			}
			// END
			gen.mark(stateDone);
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
			// dup the target. stack -> lock | lock
			gen.dup();
			// get mid. stack -> lock | lock mid
			switch (policy) {
			case INLINE:
				gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
				break;
			case PRIVATE:
			case PROTECTED:
			case PUBLIC:
				gen.push(mid);
				break;
			}
			// call acquire hook. stack -> lock |
			gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ACQUIRE);
			break;
		case Opcodes.MONITOREXIT:
			myStackSize(1);
			// dup the target. stack -> lock | lock
			gen.dup();
			// call acquire hook. stack -> lock |
			gen.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_RELEASE);
			break;
		}
		super.visitInsn(opcode);
	}

	@Override
	public void visitMultiANewArrayInsn(String arg0, int arg1) {
		// TODO allocate shadow...?
		super.visitMultiANewArrayInsn(arg0, arg1);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		originalMaxLocals = maxLocals;
		originalMaxStack = maxStack;
	}

}
