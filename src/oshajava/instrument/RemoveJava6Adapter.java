package oshajava.instrument;

import oshajava.support.org.objectweb.asm.ClassAdapter;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.MethodAdapter;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;

public class RemoveJava6Adapter extends ClassAdapter implements ClassVisitor {

	public RemoveJava6Adapter(ClassVisitor cv) {
		super(cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		if (version == Opcodes.V1_6 || version == Opcodes.V1_7) {
			super.visit(Opcodes.V1_5, access, name, signature, superName, interfaces);
		} else {
		    //TODO: This is a simple hack to allow LDC instructions to load classes.
		    // (This allows static synchronized methods to work without more
		    // work on the instrumentation end.) It could cause incompatibilities --
		    // we can change it back later.
			super.visit(Opcodes.V1_5, access, name, signature, superName, interfaces);
		}
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		return new MethodAdapter(super.visitMethod(access, name, desc, signature, exceptions)) {
			   public void visitFrame(final int type, final int nLocal, final Object[] local,
				        final int nStack, final Object[] stack) { }
		};
	}

}
