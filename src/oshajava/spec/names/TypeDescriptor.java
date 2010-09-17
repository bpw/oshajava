package oshajava.spec.names;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import oshajava.support.acme.util.Assert;

public abstract class TypeDescriptor extends Descriptor {

	private static final long serialVersionUID = 1L;

	/**
	 * Construct a type descriptor for a TypeMirror.
	 */
	public static TypeDescriptor of(TypeMirror tm, Elements util) {
		return TypeDescriptor.of(tm, util, 0);
	}
	private static TypeDescriptor of(final TypeMirror tm, Elements util, final int arrayDepth) {
//		System.out.println("TypeDescriptor.of");
		final TypeDescriptor type;
		// Reference:
		// http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1169
		// http://www.ibm.com/developerworks/java/library/j-cwt02076.html
		switch (tm.getKind()) {
		case BOOLEAN:
		case BYTE:
		case CHAR:
		case DOUBLE:
		case FLOAT:
		case INT:
		case LONG:
		case VOID:
		case SHORT:
			type = PrimitiveDescriptor.get(tm.getKind().toString().toLowerCase());
			break;
		case ARRAY:
			return TypeDescriptor.of(((ArrayType)tm).getComponentType(), util, arrayDepth + 1);
		case DECLARED:
			type = ObjectTypeDescriptor.of((DeclaredType)tm, util);
			break;
		case TYPEVAR:
//			type = new TypeParameterDescriptor(tm.toString(), (ObjectTypeDescriptor)((TypeVariable)tm).getUpperBound());
			type = ObjectTypeDescriptor.OBJECT;
//			Assert.fail("Type var case exercised.");
			// FIXME
			break;
		case WILDCARD:
			type = null;
//			type = new WildcardParameterDescriptor((ObjectTypeDescriptor)TypeDescriptor.of(((WildcardType)tm).getSuperBound().));
			Assert.fail("Wildcard case exercised.");
			break;
		case EXECUTABLE:        
		case NULL:
		case OTHER:
		case PACKAGE:
		case ERROR:
		default:
			Assert.fail("Other case exercised.");
			return null;
		}
		if (arrayDepth == 0) {
			return type;
		} else {
			return new ArrayTypeDescriptor(type, arrayDepth);
		}

	}

}
