/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.instrument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import oshajava.spec.names.FieldDescriptor;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.support.org.objectweb.asm.AnnotationVisitor;
import oshajava.support.org.objectweb.asm.Attribute;
import oshajava.support.org.objectweb.asm.ClassReader;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.FieldVisitor;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;

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
		if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && (access & Opcodes.ACC_PRIVATE) == 0) {
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
				Assert.warn("Cannot find class %s while looking for super fields!  Hoping for the best.", className);
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
