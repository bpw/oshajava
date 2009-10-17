package oshaj.instrument;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;


public class MethodInstrumentor extends GeneratorAdapter {
	
	protected final int mid;
	protected final boolean isMain;
	protected final boolean isSynchronized;
	protected final boolean isConstructor;
	protected final boolean isClinit;
	protected boolean inlined = true;
	
	protected Method readHook = null, writeHook = null, firstWriteHook = null;
	
	protected final Instrumentor inst;
	
	// tmp local var used to store target of field put/get and to store state.
	protected int tmpLocal = super.newLocal(Type.getObjectType("Ljava/lang/Object;"));
	protected int stackPeak = 0;
	
	protected Label end, handler;
	
	public MethodInstrumentor(MethodVisitor parent, int access, String name, String desc, int mid, Instrumentor inst) {
		super(parent, access, name, desc);
		this.mid = mid;
		this.inst = inst;
		if (inlined) {
			end = new Label();
			handler = new Label();
		}
		isMain = (
				(access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) 
				&& name.endsWith(".main") 
				&& desc.equals("([Ljava/lang/String;)V")
		);
		isSynchronized = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
		isConstructor = name.endsWith("<init>");
		isClinit = name.endsWith("<clinit>");
		
		readHook = Instrumentor.HOOK_PROTECTED_READ; //( inEdges ? Instrumentor.SHARED_READ_HOOK : Instrumentor.PRIVATE_READ_HOOK);
//		writeHook = ( outEdges ? Instrumentor.SHARED_WRITE_HOOK : Instrumentor.PRIVATE_WRITE_HOOK);
//		firstWriteHook = ( outEdges ? Instrumentor.SHARED_FIRST_WRITE_HOOK : Instrumentor.PRIVATE_FIRST_WRITE_HOOK);
	}
	
	protected static int max(int x, int y) {
		if (x >= y) return x;
		else return y;
	}
	
	public void setReaders(String[] readers) {
		inst.addReaderSet(mid, readers);
	}
	
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		// TODO move descriptors to constants
		if (desc.equals(Type.getDescriptor(oshaj.annotation.ThreadPrivate.class))) {
			writeHook = Instrumentor.HOOK_PRIVATE_WRITE;
			firstWriteHook = Instrumentor.HOOK_PRIVATE_FIRST_WRITE;
		} else {
			writeHook = Instrumentor.HOOK_PROTECTED_WRITE;
			firstWriteHook = Instrumentor.HOOK_PROTECTED_FIRST_WRITE;
		}
		if (! desc.equals(Type.getDescriptor(oshaj.annotation.Inline.class))) {
			inlined = false; // it's true by default.
			if (desc.equals(Type.getDescriptor(oshaj.annotation.ReadBy.class))) {
				// new BitVectorIntSet
				return new AnnotationRecorder(this);
			} else if (desc.equals(Type.getDescriptor(oshaj.annotation.ReadByAll.class))) {
				// UniversalIntSet.set
			}
		}
		final String readByClass = Type.getDescriptor(oshaj.annotation.ReadBy.class);
		if (desc.startsWith(readByClass.substring(0, readByClass.lastIndexOf('/')))) {
			return null; // if it's an oshaj annotation, no need to send it on.
		} else {
			return super.visitAnnotation(desc, visible);
		}
	}

	@Override
	public void visitCode() {
		super.visitCode();
		Label start = new Label();
		if (!inlined) {
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, Instrumentor.HOOK_ENTER);
		}
		if (isSynchronized) {
			// TODO call acquire hook.
		}
		super.visitTryCatchBlock(start, end, handler, null);
		super.visitLabel(start);
	}

	@Override
	public void visitEnd() {
		super.visitLabel(end); // TODO use same label for end and handler?
		super.visitLabel(handler);
		if (isClinit) {
			super.invokeStatic(Type.getObjectType(inst.className), 
					new Method(inst.className + "." + Instrumentor.READERSET_INIT_NAME, Instrumentor.READERSET_INIT_DESC));
		}
		if (isSynchronized) {
			// TODO call release hook.
		}
		if (!inlined) {
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
			// dup the target. stack -> obj | obj
			super.dup();
			// Push the current method id. stack -> obj | mid
			super.push(mid);
			// do a first write. stack -> obj | state
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, firstWriteHook);
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
			// Push the current method id. stack -> obj | state mid
			super.push(mid);
			// Call the write hook. stack -> obj | 
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, writeHook);
			
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
			// Push the current method id. stack -> mid
			super.push(mid);
			// do a first write. stack -> state
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, firstWriteHook);
			// store the new state. stack -> 
			super.putStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
			// jump to end. stack -> 
			super.goTo(stateDone);
			
			// NON-NULL CASE: regular write
			// stack == state
			super.mark(nonNullState);
			// Push the current method id. stack -> state mid
			super.push(mid);
			// Call the write hook. stack -> 
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, writeHook);
			
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
			super.push(mid);
			// Call the read hook. stack -> obj | 
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, readHook);
			
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
			super.push(mid);
			// Call the read hook. stack -> 
			super.invokeStatic(Instrumentor.RUNTIME_MONITOR_TYPE, readHook);
			
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
		// TODO monitorenter, monitorexit
//		if (opcode == Opcodes.MONITOREXIT) {
//			super.dup();
//			super.storeLocal(TEMP_LOCAL);
//			// Push the current method id. stack -> mid
//			super.push(id);
//			// Get the current Thread. stack -> mid thread
//			super.invokeVirtual(THREAD_TYPE, CURRENT_THREAD_METHOD);
//			// Get the current Thread's tid. stack -> mid tid
//			super.invokeVirtual(THREAD_TYPE, GET_TID_METHOD);
//			// Get the monitor object. stack -> mid tid obj
//			super.loadLocal(TEMP_LOCAL);
//			// Call the release hook. stack -> 
//
//		}
		super.visitInsn(opcode);
	}

//	@Override
//	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
//		super.visitMethodInsn(opcode, owner, name, desc);
//		if (opcode == Opcodes.INVOKESPECIAL && name.contains("\"<init>\"")) {
//			super.newInstance(Instrumentor.STATE_TYPE);
//			super.dup();
//			super.invokeConstructor(Instrumentor.STATE_TYPE, Instrumentor.STATE_CONSTRUCTOR);
//			super.loadThis();
//			super.putField(Type.getType(owner), Instrumentor.LOCK_STATE_NAME, Instrumentor.STATE_TYPE);
//		}
//	}

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
