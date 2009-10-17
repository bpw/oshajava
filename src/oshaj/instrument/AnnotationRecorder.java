package oshaj.instrument;

import org.objectweb.asm.AnnotationVisitor;

public class AnnotationRecorder implements AnnotationVisitor {

	protected final MethodInstrumentor mi;

	public AnnotationRecorder(MethodInstrumentor mi) {
		this.mi = mi;
	}

	public void visit(String name, Object value) {
		if (name.equals("readers")) {
			mi.setReaders((String[])value);
		}
	}

	public AnnotationVisitor visitAnnotation(String name, String desc) {
		return null;
	}

	public AnnotationVisitor visitArray(String name) {
		throw new RuntimeException("AnnotationRecorder.visitArray called, but unimplemented");
	}

	public void visitEnd() { }

	public void visitEnum(String name, String desc, String value) { }

}
