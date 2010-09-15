package oshajava.instrument;

import java.util.List;

import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.AdviceAdapter;


public class StaticShadowInitInserter extends AdviceAdapter {

	private final Type classType;
	private final List<String> inlineInitFields;
//	private final String className;
	public StaticShadowInitInserter(MethodVisitor mv, int access, String name, String desc, Type classType, List<String> inlineInitFields) {
		super(mv, access, name, desc);
//		this.className = name;
		this.classType = classType;
		this.inlineInitFields = inlineInitFields;
	}

    private int varCurrentThread;
    private int maxStack;
    private int maxLocals;

	@Override
	public void onMethodEnter() {
	    // Enter hook.
	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ENTER_CLINIT);
	    varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
	    super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	    
	    // Initialization.
	    if (inlineInitFields == null) {
    		super.invokeStatic(classType, ClassInstrumentor.STATIC_SHADOW_INIT_METHOD);
    	} else if (!inlineInitFields.isEmpty()) {
    	    // inlineInitFields indicates that this is an interface, and
    	    // we should initialize these fields directly in the clinit
    	    // instead of invoking the initer.
    	    int varCurrentState = super.newLocal(ClassInstrumentor.STATE_TYPE);
    	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_CURRENT_STATE);
    	    super.storeLocal(varCurrentState);
    	    for (String fieldname : inlineInitFields) {
			    // Shadow field.
				super.loadLocal(varCurrentState);
				super.putStatic(classType, fieldname + ClassInstrumentor.SHADOW_FIELD_SUFFIX, ClassInstrumentor.STATE_TYPE);
			}
    	}
	}
	
	private void exitHook() {
	    super.loadLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_EXIT);
	}
	
	@Override
	public void visitEnd() {
	    exitHook();
	    super.visitMaxs(maxStack+1, maxLocals+1);
	    super.visitEnd();
	}
	
	@Override
	public void visitInsn(int opcode) {
	    switch (opcode) {
        case IRETURN:
		case LRETURN:
		case FRETURN:
		case DRETURN:
		case ARETURN:
		case RETURN:
		    exitHook();
	    default:
			super.visitInsn(opcode);
			break;
		}
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
	    this.maxStack = maxStack;
	    this.maxLocals = maxLocals;
	}

}
