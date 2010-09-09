package oshajava.sourceinfo;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import oshajava.instrument.InstrumentationAgent;

public class DescriptorWrangler {
	/**
	 * Construct the JVM type descriptor for a TypeMirror.
	 */
	public static String typeDescriptor(TypeMirror tm) {
		// Reference:
		// http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1169
		// http://www.ibm.com/developerworks/java/library/j-cwt02076.html
		switch (tm.getKind()) {
		case BOOLEAN:
			return "Z";
		case BYTE:
			return "B";
		case CHAR:
			return "C";
		case DOUBLE:
			return "D";
		case FLOAT:
			return "F";
		case INT:
			return "I";
		case LONG:
			return "J";
		case VOID:
			return "V";
		case SHORT:
			return "S";

		case ARRAY:
			return "[" + typeDescriptor(((ArrayType)tm).getComponentType());

		case DECLARED:
			DeclaredType decl = (DeclaredType)tm;
			
//			String name = decl.toString(); // XXX Old code.
//			int lt = name.indexOf('<');
//			if (lt != -1) {
//				// This is a parameterized type. Remove the <...> part.
//				name = name.substring(0, lt);
//			}
			StringBuilder nameBuilder = new StringBuilder(decl.toString()); // XXX New code to fix old bug for Class<E>.InnerClass
			for (int l = 0; l < nameBuilder.length(); ++l) {
				if (nameBuilder.charAt(l) == '<') {
					int count = 1;
					int r;
					for (r = l+1; r < nameBuilder.length() && count != 0; ++r) {
						if (nameBuilder.charAt(r) == '<')
							count++;
						else if (nameBuilder.charAt(r) == '>')
							count--;
					}
					nameBuilder.delete(l, r);
				}
			}
			String name = InstrumentationAgent.internalName(nameBuilder.toString());

			// Disable <...> appending, because ASM doesn't seem to do it?
			/*
            if (!decl.getTypeArguments().isEmpty()) {
                // Add back type parameters.
                name += "<";
                for (TypeMirror arg : decl.getTypeArguments()) {
                    name += typeDescriptor(arg);
                }
                name += ">";
            }
			 */

			// Check if it's an inner class.
			TypeMirror encloser = null;
			if (decl.getEnclosingType().getKind() != TypeKind.NONE) {
				encloser = decl.getEnclosingType();
			} else if (decl.asElement().getEnclosingElement()
					instanceof TypeElement) {
				encloser = decl.asElement().getEnclosingElement().asType();
			}

			if (encloser != null) {
				// This is an inner class.
				int lastSlash = name.lastIndexOf('/');
				String baseName = name.substring(lastSlash + 1);
				String enclosingName = typeDescriptor(encloser);
				// Remove L and ; on either end of encloser descriptor.
				enclosingName = enclosingName.substring(1,
						enclosingName.length() - 1);
				name = enclosingName + "$" + baseName;
			}

			return "L" + name + ";";

		case TYPEVAR:
//			return "T" + tm.toString() + ";";
			return "Ljava/lang/Object;";

		case WILDCARD:
//			return "?";
			return "Ljava/lang/Object;";

		case EXECUTABLE:        
		case NULL:
		case OTHER:
		case PACKAGE:
		case ERROR:
		default:
			return null;
		}

	}

	/**
	 * Construct the JVM method descriptor for an ExecutableElement.
	 */
	public static String methodDescriptor(TypeElement cls, ExecutableElement m) {
		// Container name.
		String out = typeDescriptor(cls.asType());
		// Remove L and ; from container class.
		out = out.substring(1, out.length() - 1);

		// Method name.
		out += "." + m.getSimpleName();

		// Parameter and return types.
		out += "(";
		// Special case for enumeration constructors.
		if (cls.getKind() == ElementKind.ENUM &&
				m.getSimpleName().toString().equals("<init>")) {
			// For some reason, the annotation processing system seems
			// to miss some enum constructor parameters!
			out += "Ljava/lang/String;I";
		}
		// Special case for non-static inner class constructors.
		if (cls.getNestingKind() == NestingKind.MEMBER &&
				!cls.getModifiers().contains(Modifier.STATIC) &&
				m.getSimpleName().toString().equals("<init>")) {
			// Annotation processing is also not aware that inner class
			// constructors get their outer class passed as a parameter.
			out += typeDescriptor(cls.getEnclosingElement().asType());
			// TODO Print out inner classes here.
		}
		for (VariableElement ve : m.getParameters()) {
			out += typeDescriptor(ve.asType());
		}
		out += ")" + typeDescriptor(m.getReturnType());
		return out;
	}


}
