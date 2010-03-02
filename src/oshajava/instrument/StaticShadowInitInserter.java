package oshajava.instrument;

import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.AdviceAdapter;


public class StaticShadowInitInserter extends AdviceAdapter {

	private final Type classType;
	public StaticShadowInitInserter(MethodVisitor mv, int access, String name, String desc, Type classType) {
		super(mv, access, name, desc);
		this.classType = classType;
	}

    private int varCurrentThread;
    private int maxStack;
    private int maxLocals;

	@Override
	public void onMethodEnter() {
	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ENTER_CLINIT);
	    varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
	    super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	    
		super.invokeStatic(classType, ClassInstrumentor.STATIC_SHADOW_INIT_METHOD);
	}
	
	@Override
	public void visitEnd() {
	    super.loadLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_EXIT);
	    
	    super.visitMaxs(maxStack+1, maxLocals+1);
	    super.visitEnd();
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
	    this.maxStack = maxStack;
	    this.maxLocals = maxLocals;
	}

}
