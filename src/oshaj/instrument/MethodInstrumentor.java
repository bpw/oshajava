package oshaj.instrument;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;


public class MethodInstrumentor extends GeneratorAdapter {
	
	protected final int mid;
	protected final boolean inlined;
	protected final boolean isMain;
	protected final boolean isSynchronized;
//	protected final boolean isConstructor;
	
	protected final Method readHook, writeHook;
	
	protected static final int TEMP_LOCAL = 1;
	protected int stackPeak = 0;
	
	protected Label end, handler;
	
	public MethodInstrumentor(MethodVisitor parent, int access, String name, String desc, 
			int id, boolean inlined, boolean inEdges, boolean outEdges) {
		super(parent, access, name, desc);
		this.mid = id;
		this.inlined = inlined;
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
//		isConstructor = name.endsWith("<init>");
		readHook = ( inEdges ? Instrumentor.SHARED_READ_HOOK : Instrumentor.PRIVATE_READ_HOOK);
		writeHook = ( outEdges ? Instrumentor.SHARED_WRITE_HOOK : Instrumentor.PRIVATE_WRITE_HOOK);
	}
	
	protected static int max(int x, int y) {
		if (x >= y) return x;
		else return y;
	}

	@Override
	public void visitCode() {
		super.visitCode();
		Label start = new Label();
		if (!inlined) {
			super.invokeStatic(Instrumentor.RUNTIMEMONITOR_TYPE, Instrumentor.ENTER_HOOK);
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
		if (isSynchronized) {
			// TODO call release hook.
		}
		if (!inlined) {
			super.invokeStatic(Instrumentor.RUNTIMEMONITOR_TYPE, Instrumentor.EXIT_HOOK);
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
		// stack is initially: obj
		if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETFIELD) {
			// if instance field, dup the target. stack -> obj obj
			super.dup();
		}
		
		
		// FIXME
		
		
		// NOTE: in the stacks below, <obj> is only there if this is an
		// instance field op. It was there to start.
		
		// Push the current method id. stack -> <obj> mid
		super.push(mid);
		// Get the current Thread. stack -> <obj> mid thread
		super.invokeStatic(Instrumentor.THREAD_TYPE, Instrumentor.CURRENT_THREAD_METHOD);
		// Get the current Thread's tid. stack -> <obj> mid tid
		super.invokeVirtual(Instrumentor.THREAD_TYPE, Instrumentor.GET_TID_METHOD);
		
		final Type ownerType = Type.getType(owner);
		final String stateFieldName = name + Instrumentor.SHADOW_FIELD_SUFFIX; 
		final Label nonNullState = new Label();

		switch(opcode) {
		case Opcodes.PUTFIELD:
			// Get the State for this field. stack -> <obj> mid tid state
			super.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
			// If null, create one.
			// dup the state. stack -> <obj> mid tid state state
			super.dup();
			// if non-null, jump ahead. stack -> <obj> mid tid state
			super.ifNonNull(nonNullState);
			// if null, pop the null. stack -> <obj> mid tid
			super.pop();
			// load this. stack -> <obj> mid tid this
			super.loadThis();
			// initialize the state field. stack -> <obj> mid tid state
			super.invokeVirtual(ownerType, new Method(
					ownerType.getInternalName()+ "." + name + Instrumentor.STATE_INIT_METHOD_SUFFIX, "()V"));
			// label for non-null jump target. stack == <obj> mid tid state
			super.mark(nonNullState);
			// Call the write hook. stack -> <obj> 
			super.invokeStatic(Instrumentor.RUNTIMEMONITOR_TYPE, writeHook);
			break;
		case Opcodes.PUTSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.getStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
			// If null, create one.
			// dup the state. stack -> mid tid state state
			super.dup();
			// if non-null, jump ahead. stack -> mid tid state
			super.ifNonNull(nonNullState);
			// if null, pop the null. stack -> mid tid
			super.pop();
			// initialize the state field. stack -> mid tid state
			super.invokeVirtual(ownerType, new Method(
					ownerType.getInternalName()+ "." + name + Instrumentor.STATE_INIT_METHOD_SUFFIX, "()V"));
			// label for non-null jump target. stack == mid tid state
			super.mark(nonNullState);
			// Call the static write hook. stack ->
			super.invokeStatic(Instrumentor.RUNTIMEMONITOR_TYPE, writeHook);
			break;
		case Opcodes.GETFIELD:
			// load this
			// Get the State for this field. stack -> <obj> mid tid state
			super.getField(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
//			super.ifNull(nonNullState);
			// Call the read hook. stack -> <obj> 
			super.invokeStatic(Instrumentor.RUNTIMEMONITOR_TYPE, readHook);
			break;
		case Opcodes.GETSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.getStatic(ownerType, stateFieldName, Instrumentor.STATE_TYPE);
			// TODO we assume it's not null. (watch out for Frame changes if you do otherwise...)
			// Call the static read hook. stack ->
			super.invokeStatic(Instrumentor.RUNTIMEMONITOR_TYPE, readHook);
			break;
		}
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
