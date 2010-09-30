package oshajava.instrument;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.InflaterInputStream;

import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

import oshajava.spec.names.FieldDescriptor;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.support.org.objectweb.asm.AnnotationVisitor;
import oshajava.support.org.objectweb.asm.Attribute;
import oshajava.support.org.objectweb.asm.ClassReader;
import oshajava.support.org.objectweb.asm.FieldVisitor;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.MethodVisitor;

/**
 * Collects the fields of a class, recursively walking up the inheritance chain.
 * @author bpw
 *
 */
public class FieldCollector implements ClassVisitor {
	
	static class IOExceptionWrapper extends RuntimeException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected final IOException e;
		public IOExceptionWrapper(IOException e) {
			this.e = e;
		}
		public void toss() throws IOException {
			throw e;
		}
	}
	
	private static final List<FieldDescriptor> EMPTY = new ArrayList<FieldDescriptor>(0);
	
	protected final ClassLoader loader;
	protected final ObjectTypeDescriptor type;
	protected final List<FieldDescriptor> fields = new ArrayList<FieldDescriptor>();
	
	protected FieldCollector(final ObjectTypeDescriptor type, final ClassLoader loader) {
		this.type = type;
		this.loader = loader;
	}
	protected List<FieldDescriptor> getFields() {
		return fields;
	}
	
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		if (superName != null) {
			ObjectTypeDescriptor superType = TypeDescriptor.ofClass(superName);
			try {
				fields.addAll(collect(superType, loader));
			} catch (IOException e) {
				throw new IOExceptionWrapper(e);
			}
		}
	}

	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {
		if ((access & Opcodes.ACC_PRIVATE) == 0) {
			fields.add(FieldDescriptor.of(type, name, TypeDescriptor.fromDescriptorString(desc), access));
		}
		return null;
	}

	private static final HashMap<ObjectTypeDescriptor,List<FieldDescriptor>> table = new HashMap<ObjectTypeDescriptor,List<FieldDescriptor>>();

	public static List<FieldDescriptor> collect(final ObjectTypeDescriptor className, final ClassLoader loader) throws IOException {
		Debug.debugf("fieldCollector", "collect(\"%s\", ...)", className);
		if (className.equals(TypeDescriptor.OBJECT)) return EMPTY;
//		if (InstrumentationAgent.isUntouchable(className)) return EMPTY;
		if (table.containsKey(className)) {
			return table.get(className);
		}
		FieldCollector fc = new FieldCollector(className, loader);
		try {
			new ClassReader(loader.getResourceAsStream(className.getInternalName() + ".class")).accept(fc, ClassReader.SKIP_CODE);
		} catch (IOExceptionWrapper e) {
			if (Filter.shouldInstrument(className)) {
				e.toss();
			} else {
				Assert.warn("Cannot find class %s while looking for super fields!  Hoping for the best.", className); InflaterInputStream i;
				return EMPTY;
			}
		}
		table.put(className, fc.getFields());
		return fc.getFields();
	}
	
	public void visitSource(String source, String debug) {
		// TODO Auto-generated method stub
		
	}

	public void visitOuterClass(String owner, String name, String desc) {
		// TODO Auto-generated method stub
		
	}

	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		// TODO Auto-generated method stub
		return null;
	}

	public void visitAttribute(Attribute attr) {
		// TODO Auto-generated method stub
		
	}

	public void visitInnerClass(String name, String outerName,
			String innerName, int access) {
		// TODO Auto-generated method stub
		
	}

	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		// TODO Auto-generated method stub
		return null;
	}

	public void visitEnd() {
		// TODO Auto-generated method stub
		
	}
}
