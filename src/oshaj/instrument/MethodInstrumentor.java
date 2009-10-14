package oshaj.instrument;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;


public class MethodInstrumentor extends GeneratorAdapter {
	
	protected static final Type THREAD_TYPE = Type.getType(Thread.class);
	protected static final Method CURRENT_THREAD_METHOD = new Method("java.lang.Thread.currentThread", THREAD_TYPE, new Type[0]);
	protected static final Method GET_TID_METHOD = new Method("java.lang.Thread.getTid", Type.LONG_TYPE, new Type[0]);
		
	protected final int id;
	protected final boolean inlined;
	protected final Method readHook, writeHook;
	
	protected static final int TEMP_LOCAL = 1;
	protected int stackPeak = 0;
	
	public MethodInstrumentor(MethodVisitor parent, int access, String name, String desc,
			int id, boolean inlined, boolean inEdges, boolean outEdges) {
		super(parent, access, name, desc);
		this.id = id;
		this.inlined = inlined;
		readHook = ( inEdges ? Instrumentor.SHARED_READ_HOOK : Instrumentor.PRIVATE_READ_HOOK);
		writeHook = ( outEdges ? Instrumentor.SHARED_WRITE_HOOK : Instrumentor.PRIVATE_WRITE_HOOK);
	}
	
	protected static int max(int x, int y) {
		if (x >= y) return x;
		else return y;
	}

	@Override
	public void visitCode() {
		// TODO if not inlined, insert enter hook.
		super.visitCode();
	}

	@Override
	public void visitEnd() {
		// TODO if not inlined, insert exit hook, also at any other exit point.
		// Actually, do:
		// try { enter(id); ... method body ... } finally { exit(id) }
		// TODO if it's synchronized, add acquire, release.
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
		if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETFIELD) {
			// save the target object.
			
		}
		// Push the current method id. stack -> mid
		super.push(id);
		// Get the current Thread. stack -> mid thread
		super.invokeVirtual(THREAD_TYPE, CURRENT_THREAD_METHOD);
		// Get the current Thread's tid. stack -> mid tid
		super.invokeVirtual(THREAD_TYPE, GET_TID_METHOD);
		switch(opcode) {
		case Opcodes.PUTFIELD:
			// Get the State for this field. stack -> mid tid state
			super.getField(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the write hook. stack ->
			super.invokeVirtual(Instrumentor.STATE_TYPE, writeHook);
			break;
		case Opcodes.PUTSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.getStatic(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the static write hook. stack ->
			super.invokeVirtual(Instrumentor.STATE_TYPE, writeHook);
			break;
		case Opcodes.GETFIELD:
			// Get the State for this field. stack -> mid tid state
			super.getField(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the read hook. stack ->
			super.invokeVirtual(Instrumentor.STATE_TYPE, readHook);
			break;
		case Opcodes.GETSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.getStatic(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the static read hook. stack ->
			super.invokeVirtual(Instrumentor.STATE_TYPE, readHook);
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
