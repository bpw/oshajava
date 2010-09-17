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
 * TODO support generics.
 * @author bpw
 *
 */
public class MethodDescriptor extends Descriptor {
	
	private static final long serialVersionUID = 1L;
	
	protected final CanonicalName className;
	protected final String methodName;
	protected final TypeDescriptor returnType;
	protected final List<TypeDescriptor> paramTypes;
	
	/**
	 * Construct the JVM method descriptor for an ExecutableElement.
	 */
	protected MethodDescriptor(ExecutableElement m, Elements util) {
		TypeElement cls = (TypeElement)m.getEnclosingElement();
		this.className = CanonicalName.of(cls, util);
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

	private MethodDescriptor(CanonicalName className, String methodName, String methodTypeDescriptor, String sig) {
		this.className = className;
		this.methodName = methodName;
		this.paramTypes  = new ArrayList<TypeDescriptor>();
//		if (sig != null && !sig.isEmpty()) {
//			Assert.warn("Uh oh. We should do something with method signatures: %s.%s has sig %s", className.toInternalString(), methodName,  sig);
//		}
		
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
					type = ObjectTypeDescriptor.of(CanonicalName.of(desc.substring(start + arrayDepth + 1, semi)));
					start = semi + 1;
				} else {
					type = PrimitiveDescriptor.get(desc.substring(start + arrayDepth, start + arrayDepth + 1));
					start++;
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
			type = ObjectTypeDescriptor.of(CanonicalName.of(returnDesc.substring(arrayDepth + 1, returnDesc.length() - 1)));
		} else {
			type = PrimitiveDescriptor.get(returnDesc.substring(arrayDepth, arrayDepth + 1));
		}
		if (arrayDepth == 0) {
			returnType = type;
		} else {
			returnType = new ArrayTypeDescriptor(type, arrayDepth);
		}
	}
	
	public CanonicalName getClassName() {
		return className;
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
	
	public String toSourceString() {
		String out = "";
		if (methodName.equals("<init>")) {
			out += className.toSourceString() + '.';
			String cls = className.getSimpleName();
			int i = cls.lastIndexOf('.');
			out += i == -1 ? cls : cls.substring(i);
		} else {
			out += returnType.toSourceString() + ' ' + className.toSourceString() + '.' + methodName;
		}
		out += '(';
		for (TypeDescriptor p : paramTypes) {
			out += p.toSourceString() + ", ";
		}
		if (!paramTypes.isEmpty()) {
			out = out.substring(0, out.length() - 2);
		}
		return out + ')';
	}
	
	public String toInternalString() {
		String out = className.toInternalString() + '.' + methodName + '(';
		for (TypeDescriptor p : paramTypes) {
			out += p.toInternalString();
		}
		return out + ')' + returnType.toInternalString();
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

	public static MethodDescriptor of(CanonicalName className, String methodName, String methodTypeDescriptor, String sig) {
		String s = className.toInternalString() + '.' + methodName + methodTypeDescriptor;
		if (stringToDescriptor.containsKey(s)) {
			return stringToDescriptor.get(s);
		} else {
			MethodDescriptor d = new MethodDescriptor(className, methodName, methodTypeDescriptor, sig);
			stringToDescriptor.put(s, d);
			return d;
		}
	}
}
