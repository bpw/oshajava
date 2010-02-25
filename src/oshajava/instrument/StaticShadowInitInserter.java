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

	@Override
	public void onMethodEnter() {
		super.invokeStatic(classType, ClassInstrumentor.STATIC_SHADOW_INIT_METHOD);
	}

}
