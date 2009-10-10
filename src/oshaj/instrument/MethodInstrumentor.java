package oshaj.instrument;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import oshaj.runtime.State;


public class MethodInstrumentor extends MethodAdapter {
	
	protected static final String THREAD_TYPE = Type.getType(Thread.class).getInternalName();
	protected static final String CURRENT_THREAD_METHOD = "currentThread";
	protected static final String THREAD_RET_TYPE = "()" + THREAD_TYPE;
	protected static final String GET_TID_METHOD = "currentThread";
	protected static final String LONG_RET_TYPE = "()J";
	
	protected final int id;
	protected final boolean inlined;
	protected final String readHookName, readHookDesc, writeHookName, writeHookDesc;
	
	public MethodInstrumentor(MethodVisitor parent, int id, boolean inlined, boolean inEdges, boolean outEdges) {
		super(parent);
		this.id = id;
		this.inlined = inlined;
		if (inEdges) {
			readHookName = State.PRIVATE_READ_NAME;
			readHookDesc = State.PRIVATE_READ_DESC;
		} else {
			readHookName = State.SHARED_READ_NAME;
			readHookDesc = State.SHARED_READ_DESC;
		}
		if (outEdges) {
			writeHookName = State.PRIVATE_WRITE_NAME;
			writeHookDesc = State.PRIVATE_WRITE_DESC;
		} else {
			writeHookName = State.SHARED_WRITE_NAME;
			writeHookDesc = State.SHARED_WRITE_DESC;			
		}
	}
	
	/***************************************/

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
		// Does exit need id?
		super.visitEnd();
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		// Push the current method id. stack -> mid
		super.visitLdcInsn(id);
		// Get the current Thread. stack -> mid thread
		super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREAD_TYPE, CURRENT_THREAD_METHOD, THREAD_RET_TYPE);
		// Get the current Thread's tid. stack -> mid tid
		super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREAD_TYPE, GET_TID_METHOD, LONG_RET_TYPE);
		switch(opcode) {
		case Opcodes.PUTFIELD:
			// Get the State for this field. stack -> mid tid state
			super.visitFieldInsn(Opcodes.GETFIELD, owner, name + Instrumentor.SHADOW_FIELD_SUFFIX, State.TYPE);
			// Call the write hook.
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, State.TYPE, writeHookName, writeHookDesc);
			break;
		case Opcodes.PUTSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.visitFieldInsn(Opcodes.GETSTATIC, owner, name + Instrumentor.SHADOW_FIELD_SUFFIX, State.TYPE);
			// Call the static write hook.
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, State.TYPE, writeHookName, writeHookDesc);
			break;
		case Opcodes.GETFIELD:
			// Get the State for this field. stack -> mid tid state
			super.visitFieldInsn(Opcodes.GETFIELD, owner, name + Instrumentor.SHADOW_FIELD_SUFFIX, State.TYPE);
			// Call the read hook.
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, State.TYPE, readHookName, readHookDesc);
			break;
		case Opcodes.GETSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.visitFieldInsn(Opcodes.GETSTATIC, owner, name + Instrumentor.SHADOW_FIELD_SUFFIX, State.TYPE);
			// Call the static read hook.
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, State.TYPE, readHookName, readHookDesc);
			break;
		}
		// Do the actual op.
		super.visitFieldInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitInsn(int arg0) { 
		// TODO array load and store: {a,b,c,d,f,i,l,s}a{load,store}
		super.visitInsn(arg0);
	}

	@Override
	public void visitIntInsn(int arg0, int arg1) {
		// TODO Auto-generated method stub
		super.visitIntInsn(arg0, arg1);
	}

	@Override
	public void visitLabel(Label arg0) { 
		// TODO maybe
		super.visitLabel(arg0);
	}

	@Override
	public void visitLineNumber(int arg0, Label arg1) {
		// TODO This is the line number from the source file from which the
		// class file was compiled!
		super.visitLineNumber(arg0, arg1);
	}

	@Override
	public void visitLocalVariable(String arg0, String arg1, String arg2,
			Label arg3, Label arg4, int arg5) { }

	@Override
	public void visitMultiANewArrayInsn(String arg0, int arg1) {
		// TODO allocate shadow...?
		super.visitMultiANewArrayInsn(arg0, arg1);
	}

	@Override
	public void visitTryCatchBlock(Label arg0, Label arg1, Label arg2,
			String arg3) {
		// TODO Auto-generated method stub
		super.visitTryCatchBlock(arg0, arg1, arg2, arg3);
	}

}
