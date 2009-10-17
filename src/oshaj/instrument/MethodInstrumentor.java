package oshaj.instrument;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import oshaj.util.BitVectorIntSet;
import oshaj.util.UniversalIntSet;


public class MethodInstrumentor extends GeneratorAdapter {
	
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
	
	protected int stackPeak = 0;
	
	protected final Label end = new Label(), handler = new Label();
	
	public MethodInstrumentor(MethodVisitor parent, int access, String name, String desc, Instrumentor inst) {
		super(parent, access, name, desc);
		this.inst = inst;
		isStatic = (access & Opcodes.ACC_STATIC) != 0;
		isMain = (access & Opcodes.ACC_PUBLIC ) != 0 && isStatic
			&& name.equals("main") && desc.equals("([Ljava/lang/String;)V");
		isSynchronized = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
		isConstructor = name.equals("\"<init>\"");
//		isClinit = name.equals("\"<clinit>\"");
		nameAndDesc = name + desc;
	}
	
	protected static int max(int x, int y) {
		if (x >= y) return x;
		else return y;
	}
	
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		// TODO move descriptors to constants
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
		} else {
			// Not one of ours. Inline by default.
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
	public void visitCode() {
		super.visitCode();
		Label start = new Label();
		if (policy != Policy.INLINE) {
			super.push(mid);
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ENTER);
		}
		if (isSynchronized) {
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
		}
		super.visitTryCatchBlock(start, end, handler, null);
		super.visitLabel(start);
	}

	@Override
	public void visitEnd() {
		super.visitLabel(end); // TODO use same label for end and handler?
		super.visitLabel(handler);
		if (isSynchronized) {
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
		super.visitEnd();
	}
	
	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
		super.visitFrame(type, nLocal, local, nStack, stack);
	}
	
	/**
	 * Instrument accesses with read and write hooks.
	 * 
	 * Puts one int, one long, and one address on the stack.	
	 */
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {		

		// TODO figure out how to add visitFrame where needed below (GOTOs) to
		// avoid cost of computing the frames...?
		
		final Type ownerType = Type.getType(owner);
		final String stateFieldName = name + Instrumentor.SHADOW_FIELD_SUFFIX; 
		final Label nonNullState = new Label(), stateDone = new Label();

		switch(opcode) {
		case Opcodes.PUTFIELD:
			// dup the target. stack -> obj | obj
			super.dup();
			// Get the State for this field. stack -> obj | state
			super.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
			// dup the state. stack -> obj | state state
			super.dup();
			// if non-null, jump ahead. stack -> obj | state
			super.ifNonNull(nonNullState);
			
			// NULL CASE: first write
			// pop the null. stack -> obj | 
			super.pop();
			switch (policy) {
			case INLINE:
				// get the current method ID. stack -> obj | mid
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
				// load the readerset. stack -> obj | mid set
				// do a first write. stack -> obj | state
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
				break;
			case PROTECTED:
				// Push the current method id. stack -> obj | mid
				super.push(mid);
				// do a first write. stack -> obj | state
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_FIRST_WRITE);
				break;
			case PUBLIC:
				// Push the current method id. stack -> obj | mid
				super.push(mid);
				// do a first write. stack -> obj | state
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_FIRST_WRITE);
				break;
			case PRIVATE:
				// Push the current method id. stack -> obj | mid
				super.push(mid);
				// do a first write. stack -> obj | state
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_FIRST_WRITE);
				break;
			}
			// swap. stack -> state | obj
			super.swap();
			// dup down 1. -> obj | state obj
			super.dupX1();
			// store the new state. stack -> obj | 
			super.putField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
			// jump to end. stack -> obj | 
			super.goTo(stateDone);
			
			// NON-NULL CASE: regular write
			// stack == obj | state
			super.mark(nonNullState);
			switch (policy) {
			case INLINE:
				// Push the current method id. stack -> obj | state mid
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_MID);
				// Call the write hook. stack -> obj | 
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
				break;
			case PROTECTED:
				// Push the current method id. stack -> obj | state mid
				super.push(mid);
				// Call the write hook. stack -> obj | 
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PROTECTED_WRITE);
				break;
			case PUBLIC:
				// Push the current method id. stack -> obj | state mid
				super.push(mid);
				// Call the write hook. stack -> obj | 
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PUBLIC_WRITE);
				break;
			case PRIVATE:
				// Push the current method id. stack -> obj | state mid
				super.push(mid);
				// Call the write hook. stack -> obj | 
				super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_PRIVATE_WRITE);
				break;
			}
			
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
				// load the readerset. stack -> mid set
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
			
			break;
		}
		// END
		super.mark(stateDone);
		// Do the actual op.
		super.visitFieldInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitInsn(int opcode) { 
		// TODO array load and store: {a,b,c,d,f,i,l,s}a{load,store}
		switch (opcode) {
		case Opcodes.MONITORENTER:
			// dup the target. stack -> lock | lock
			super.dup();
			// get mid. stack -> lock | lock mid
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
			// call acquire hook. stack -> lock |
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ACQUIRE);
			break;
		case Opcodes.MONITOREXIT:
			// dup the target. stack -> lock | lock
			super.dup();
			// call acquire hook. stack -> lock |
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_RELEASE);
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
	public void visitVarInsn(int opcode, int var) {
		// We use local 1 as a temp, so increment var.
		super.visitVarInsn(opcode, (var > 0 ? var + 1 : var));
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		// We use local 1 as a temp, so increment maxLocals.
		super.visitMaxs(maxStack + stackPeak, maxLocals + 1);
	}

}
