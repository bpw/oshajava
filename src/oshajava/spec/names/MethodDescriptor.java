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

package oshajava.spec.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * TODO support generics?  Right now we just do type erasure.  TODO Double check that we do the *right* erasure!
 * @author bpw
 *
 */
public class MethodDescriptor extends Descriptor {
	
	private static final long serialVersionUID = 1L;
	
	protected final ObjectTypeDescriptor classType;
	protected final String methodName;
	protected final TypeDescriptor returnType;
	protected final List<TypeDescriptor> paramTypes;
	
	@Override
	public int hashCode() {
		return classType.hashCode() ^ methodName.hashCode() ^ returnType.hashCode() ^ paramTypes.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other instanceof MethodDescriptor) {
			final MethodDescriptor md = (MethodDescriptor)other;
			return classType.equals(md.classType) && methodName.equals(md.methodName) && returnType.equals(md.returnType) && paramTypes.equals(md.paramTypes);
		}
		return false;
	}
	
	/**
	 * Construct the JVM method descriptor for an ExecutableElement.
	 */
	private MethodDescriptor(ExecutableElement m, Elements util) {
		TypeElement cls = (TypeElement)m.getEnclosingElement();
		this.classType = TypeDescriptor.ofClass(cls, util);
		this.methodName = m.getSimpleName().toString();
		this.paramTypes = new ArrayList<TypeDescriptor>();
		this.returnType = TypeDescriptor.of(m.getReturnType(), util);
		
		// Handle the implicit outer class param for non-static inner classes.
		if (methodName.equals("<init>")) {
			TypeMirror t = ((DeclaredType)cls.asType()).getEnclosingType(); 
			if (t.getKind() == TypeKind.DECLARED) {
				paramTypes.add(TypeDescriptor.of(t, util));
			}
		}

		// Add normal parameter types.
		ExecutableType methodType = (ExecutableType)m.asType();
		for (TypeMirror p : methodType.getParameterTypes()) {
			paramTypes.add(TypeDescriptor.of(p, util));
		}
		
	}

	private MethodDescriptor(ObjectTypeDescriptor classType, String methodName, String methodTypeDescriptor, String sig) {
		this.classType = classType;
		this.methodName = methodName;
		this.paramTypes  = new ArrayList<TypeDescriptor>();
		
		{
			final String desc = methodTypeDescriptor.substring(methodTypeDescriptor.indexOf('(') + 1, methodTypeDescriptor.lastIndexOf(')'));
			int start = 0;
			while (start < desc.length()) {
				int arrayDepth = 0;
				while (desc.charAt(start + arrayDepth) == '[') {
					arrayDepth++;
				}
				final TypeDescriptor type;
				if (desc.charAt(start + arrayDepth) == 'L') {
					int semi = desc.indexOf(';', start + arrayDepth);
					type = TypeDescriptor.ofClass(desc.substring(start + arrayDepth + 1, semi));
					start = semi + 1;
				} else {
					type = TypeDescriptor.ofPrimitive(desc.substring(start + arrayDepth, start + arrayDepth + 1));
					start = start + arrayDepth + 1;
				}
				if (arrayDepth == 0) {
					paramTypes.add(type);
				} else {
					paramTypes.add(new ArrayTypeDescriptor(type, arrayDepth));
				}
			}
		}
		
		final String returnDesc = methodTypeDescriptor.substring(methodTypeDescriptor.lastIndexOf(')') + 1);
		int arrayDepth = 0;
		while (returnDesc.charAt(arrayDepth) == '[') {
			arrayDepth++;
		}
		final TypeDescriptor type;
		if (returnDesc.charAt(arrayDepth) == 'L') {
			type = TypeDescriptor.ofClass(returnDesc.substring(arrayDepth + 1, returnDesc.length() - 1));
		} else {
			type = TypeDescriptor.ofPrimitive(returnDesc.substring(arrayDepth, arrayDepth + 1));
		}
		if (arrayDepth == 0) {
			returnType = type;
		} else {
			returnType = new ArrayTypeDescriptor(type, arrayDepth);
		}
	}
	
	public ObjectTypeDescriptor getClassType() {
		return classType;
	}

	public String getMethodName() {
		return methodName;
	}

	public TypeDescriptor getReturnType() {
		return returnType;
	}

	public List<TypeDescriptor> getParamTypes() {
		return Collections.unmodifiableList(paramTypes);
	}
	
	public boolean isConstructor() {
		return methodName.equals("<init>");
	}
	
	public boolean isClassInit() {
		return methodName.equals("<clinit>");
	}
	
	public String getSourceName() {
		String out = "";
		if (isConstructor()) {
			out += classType.getSourceName() + '.';
			String cls = classType.getTypeName().getSimpleName();
			int i = cls.lastIndexOf('.');
			out += i == -1 ? cls : cls.substring(i);
		} else {
			out += returnType.getSourceName() + ' ' + classType.getSourceName() + '.' + methodName;
		}
		return out;
	}
	
	public String getInternalName() {
		return classType.getInternalName() + '.' + methodName;
	}
	
	public String getSourceDescriptor() {
		String out = getSourceName() + '(';
		for (TypeDescriptor p : paramTypes) {
			out += p.getSourceName() + ", ";
		}
		if (!paramTypes.isEmpty()) {
			out = out.substring(0, out.length() - 2);
		}
		return out + ')';
	}
	
	public String getInternalDescriptor() {
		String out = getInternalName() + '(';
		for (TypeDescriptor p : paramTypes) {
			out += p.getInternalName();
		}
		return out + ')' + returnType.getInternalName();
	}
	
	/*******************************************/
	
	// Compile time.
	private static final Map<ExecutableElement,MethodDescriptor> methodToDescriptor = new HashMap<ExecutableElement,MethodDescriptor>();
	
	public static MethodDescriptor of(ExecutableElement m, Elements util) {
		if (methodToDescriptor.containsKey(m)) {
			return methodToDescriptor.get(m);
		} else {
			MethodDescriptor d = new MethodDescriptor(m, util);
			methodToDescriptor.put(m, d);
			return d;
		}
	}
	
	// Runtime.
	private static final Map<String,MethodDescriptor> stringToDescriptor = new HashMap<String,MethodDescriptor>();

	public static MethodDescriptor of(ObjectTypeDescriptor classType, String methodName, String methodTypeDescriptor, String sig) {
		String s = classType.getInternalName() + '.' + methodName + methodTypeDescriptor;
		if (stringToDescriptor.containsKey(s)) {
			return stringToDescriptor.get(s);
		} else {
			MethodDescriptor d = new MethodDescriptor(classType, methodName, methodTypeDescriptor, sig);
			stringToDescriptor.put(s, d);
			return d;
		}
	}
}
