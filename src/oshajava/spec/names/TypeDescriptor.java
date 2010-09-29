package oshajava.spec.names;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import oshajava.support.acme.util.Assert;
import oshajava.support.org.objectweb.asm.Type;

public abstract class TypeDescriptor extends Descriptor {

	private static final long serialVersionUID = 1L;

	public static final ObjectTypeDescriptor OBJECT = ofClass("java.lang.Object");

	protected transient Type asmType;
	
	public Type getAsmType() {
		return Type.getType(getInternalDescriptor());
	}
	
	/*******************************/

	private static final Map<String,PrimitiveDescriptor> primitives = new HashMap<String,PrimitiveDescriptor>();
	static {
		PrimitiveDescriptor[] primitiveTypes = {
				PrimitiveDescriptor.BOOLEAN,
				PrimitiveDescriptor.BYTE,
				PrimitiveDescriptor.CHAR,
				PrimitiveDescriptor.DOUBLE,
				PrimitiveDescriptor.FLOAT,
				PrimitiveDescriptor.INT,
				PrimitiveDescriptor.LONG,
				PrimitiveDescriptor.SHORT,
				PrimitiveDescriptor.VOID
		};
		for (PrimitiveDescriptor p : primitiveTypes) {
			primitives.put(p.getInternalDescriptor(), p);
			primitives.put(p.getSourceDescriptor(), p);
		}
	}
	
	public static boolean isPrimitive(String name) {
		return primitives.containsKey(name);
	}
	
	
	/**
	 * Takes names of the form a.b.C$D$E or a/b/C$D$E, but NOT a.b.C.D.E or a/b/C.D.E
	 * @param name
	 * @return
	 */
	public static ObjectTypeDescriptor ofClass(String name) {
		return CanonicalName.of(name).getType(); // FIXME may be null
	}
	public static ObjectTypeDescriptor ofClass(TypeElement t, Elements util) {
		return CanonicalName.of(t, util).getType();
	}

	public static PrimitiveDescriptor ofPrimitive(String name) {
		PrimitiveDescriptor p =  primitives.get(name);
		Assert.assertTrue(p != null, "No such primitive %s!", name);
		return p;
	}
	
	/**
	 * Construct a type descriptor for a ReferenceType TypeMirror.
	 */
	public static TypeDescriptor of(TypeMirror tm, Elements util) {
		return TypeDescriptor.of(tm, util, 0);
	}
	private static TypeDescriptor of(final TypeMirror tm, Elements util, final int arrayDepth) {
		final TypeDescriptor type;
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
			type = ofPrimitive(tm.getKind().toString().toLowerCase());
			break;
		case ARRAY:
			return of(((ArrayType)tm).getComponentType(), util, arrayDepth + 1);
		case DECLARED:
			type = ofClass((TypeElement)((DeclaredType)tm).asElement(), util);
			break;
		case TYPEVAR:
			type = OBJECT;
			// TODO Find a concrete upper bound instead?
			break;
		case WILDCARD:
			type = OBJECT;
			// TODO Find a concrete upper bound instead?
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
	
	public static TypeDescriptor fromDescriptorString(String desc) {
		int arrayDepth = 0;
		while (desc.charAt(arrayDepth) == '[') {
			arrayDepth++;
		}
		final TypeDescriptor type;
		if (desc.charAt(arrayDepth) == 'L') {
			type = TypeDescriptor.ofClass(desc.substring(arrayDepth + 1, desc.length() - 1));
		} else {
			type = TypeDescriptor.ofPrimitive(desc.substring(arrayDepth));
		}
		if (arrayDepth == 0) {
			return type;
		} else {
			 return new ArrayTypeDescriptor(type, arrayDepth);
		}

	}

}
