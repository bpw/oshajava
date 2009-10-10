package oshaj.instrument;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import oshaj.runtime.State;


public class MethodInstrumentor extends GeneratorAdapter {
	
	protected static final Type THREAD_TYPE = Type.getType(Thread.class);
	protected static final Method CURRENT_THREAD_METHOD = new Method("java.lang.Thread.currentThread", THREAD_TYPE, new Type[0]);
	protected static final Method GET_TID_METHOD = new Method("java.lang.Thread.getTid", Type.LONG_TYPE, new Type[0]);
		
	protected final int id;
	protected final boolean inlined;
	protected final Method readHook, writeHook;
	
	public MethodInstrumentor(MethodVisitor parent, int access, String name, String desc,
			int id, boolean inlined, boolean inEdges, boolean outEdges) {
		super(parent, access, name, desc);
		this.id = id;
		this.inlined = inlined;
		readHook = ( inEdges ? Instrumentor.SHARED_READ_HOOK : Instrumentor.PRIVATE_READ_HOOK);
		writeHook = ( outEdges ? Instrumentor.SHARED_WRITE_HOOK : Instrumentor.PRIVATE_WRITE_HOOK);
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
		super.push(id);
		// Get the current Thread. stack -> mid thread
		super.invokeVirtual(THREAD_TYPE, CURRENT_THREAD_METHOD);
		// Get the current Thread's tid. stack -> mid tid
		super.invokeVirtual(THREAD_TYPE, GET_TID_METHOD);
		switch(opcode) {
		case Opcodes.PUTFIELD:
			// Get the State for this field. stack -> mid tid state
			super.getField(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the write hook.
			super.invokeVirtual(Instrumentor.STATE_TYPE, writeHook);
			break;
		case Opcodes.PUTSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.getStatic(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the static write hook.
			super.invokeVirtual(Instrumentor.STATE_TYPE, writeHook);
			break;
		case Opcodes.GETFIELD:
			// Get the State for this field. stack -> mid tid state
			super.getField(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the read hook.
			super.invokeVirtual(Instrumentor.STATE_TYPE, readHook);
			break;
		case Opcodes.GETSTATIC:
			// Get the State for this field. stack -> mid tid state
			super.getStatic(Type.getType(owner), name + Instrumentor.SHADOW_FIELD_SUFFIX, Instrumentor.STATE_TYPE);
			// Call the static read hook.
			super.invokeVirtual(Instrumentor.STATE_TYPE, readHook);
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
	public void visitMultiANewArrayInsn(String arg0, int arg1) {
		// TODO allocate shadow...?
		super.visitMultiANewArrayInsn(arg0, arg1);
	}

}
