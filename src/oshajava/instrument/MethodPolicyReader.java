package oshajava.instrument;


import oshajava.sourceinfo.BitVectorIntSet;
import oshajava.sourceinfo.MethodTable;
import oshajava.sourceinfo.UniversalIntSet;
import oshajava.support.acme.util.Util;
import oshajava.support.org.objectweb.asm.AnnotationVisitor;
import oshajava.support.org.objectweb.asm.Label;
import oshajava.support.org.objectweb.asm.MethodAdapter;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.AdviceAdapter;

public class MethodPolicyReader extends MethodAdapter {

	// TODO policy names: @Inline, @Private, @ShareSelf, @ShareProtected, @SharePublic
	// @Inline, @Private, @Self, @Group, @World
	// @Inline, @Private, @Self, @Some, @All
	// @Inline, @Private, @Self, @Shared, @Global
	public static enum Policy { INLINE, PRIVATE, PROTECTED, PUBLIC }

	// TODO make this a command line option, and a per-class option
	protected static final Policy POLICY_DEFAULT = Policy.INLINE;

	protected Policy policy;

	protected final ClassInstrumentor inst;

	protected final String fullNameAndDesc;
	
	protected int mid;

	protected final boolean isMain;
	protected final boolean isSynchronized;
	protected final boolean isConstructor;
	protected final boolean isClinit;
	protected final boolean isStatic;

	public MethodPolicyReader(ClassInstrumentor inst, MethodVisitor next, int access, String name, String desc) {
		super(new MethodInstrumentor(next, access, name, desc, inst));
		this.inst = inst;
		isStatic = (access & Opcodes.ACC_STATIC) != 0;
		isMain = (access & Opcodes.ACC_PUBLIC ) != 0 && isStatic 
			&& name.equals("main") && desc.equals("([Ljava/lang/String;)V");
		isSynchronized = (access & Opcodes.ACC_SYNCHRONIZED) != 0;
		isConstructor = name.equals("<init>");
		isClinit = name.equals("<clinit>");
		fullNameAndDesc = inst.className + "." + name + desc;
//		this.policy = inst.policy();
	}
	/**
	 * If the current state is not null, jump to l.
	 * @param l
	 */
//	protected void ifNullState(Label l) {}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		// Only one osha annotation allowed per method.
//		Util.assertTrue(policy == null, "Only one policy allowed per method.");
		if (policy != null) {
			Util.log("Replacing previous policy...");
		}

		if (desc.equals(ClassInstrumentor.ANNOT_INLINE_DESC)) {
			policy = Policy.INLINE;
			return null;
		} else if (desc.equals(ClassInstrumentor.ANNOT_THREAD_PRIVATE_DESC)) {
			policy = Policy.PRIVATE;
			mid = MethodTable.register(fullNameAndDesc, null);
			return null;
		} else if (desc.equals(ClassInstrumentor.ANNOT_READ_BY_DESC)) {
			policy = Policy.PROTECTED;
			final BitVectorIntSet readerSet = new BitVectorIntSet();
			mid = MethodTable.register(fullNameAndDesc, readerSet);
			return new AnnotationRecorder(readerSet);
		} else if (desc.equals(ClassInstrumentor.ANNOT_READ_BY_ALL_DESC)) {
			policy = Policy.PUBLIC;
			mid = MethodTable.register(fullNameAndDesc, UniversalIntSet.set);
			//			Util.logf("%s (mid = %d) is ReadByAll. set in table = %s", fullNameAndDesc, mid, MethodRegistry.policyTable[mid]);
			return null;
		}  else {
			// Not one of ours.
			return super.visitAnnotation(desc, visible);
		}
	}

	class AnnotationRecorder implements AnnotationVisitor {
		protected final BitVectorIntSet readerSet;

		public AnnotationRecorder(BitVectorIntSet readerSet) {
			this.readerSet = readerSet;
		}

		public void visit(String name, Object value) {
			if (name == null) {
				MethodTable.requestID((String)value, readerSet);
			} else if (name.equals("value")) {
				Util.log("add " + name);
				for (String m : (String[])value) {
					MethodTable.requestID(m, readerSet);
				}
			}
		}

		public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
		public AnnotationVisitor visitArray(String name) {
			return name.equals("value") ? this : null;
		}
		public void visitEnd() { }
		public void visitEnum(String name, String desc, String value) { }
	}

	@Override
	public void visitCode() {
		if (policy == null) {
			if (isMain || isClinit) {
				policy = Policy.PUBLIC;
				mid = MethodTable.register(fullNameAndDesc, UniversalIntSet.set);
			} else {
				policy = POLICY_DEFAULT;
			}
			switch(policy) {
			case PUBLIC:
				mid = MethodTable.register(fullNameAndDesc, UniversalIntSet.set);
				break;
			case PROTECTED:
				Util.fail("not sure what to do here. think this is prohibited.");
				break;
			case PRIVATE:
				mid = MethodTable.register(fullNameAndDesc, null);
				break;
			case INLINE:
				break;
			}
		}
		super.visitCode();
	}

}
