package oshajava.instrument;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
			super.visit(version, access, name, signature, superName, interfaces);
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
