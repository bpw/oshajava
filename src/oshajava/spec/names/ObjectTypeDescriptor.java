package oshajava.spec.names;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

public class ObjectTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
	public static final ObjectTypeDescriptor OBJECT = new ObjectTypeDescriptor(CanonicalName.of("java.lang.Object"));
	
	protected final CanonicalName typeName;
	
	protected ObjectTypeDescriptor(CanonicalName typeName) {
		this.typeName = typeName;
	}
	
	public String toSourceString() {
		return typeName.toSourceString();
	}

	public String toInternalString() {
		return "L" + typeName.toInternalString() + ";";
	}
	
	/*******************************/

	public static ObjectTypeDescriptor of(final CanonicalName name) {
//		System.out.println("ObjectTypeDescriptor.of");
		return name.getType();
	}
	public static ObjectTypeDescriptor of(DeclaredType type, Elements util) {
//		System.out.println("ObjectTypeDescriptor.of");
		return CanonicalName.of((TypeElement)type.asElement(), util).getType();
	}
}

