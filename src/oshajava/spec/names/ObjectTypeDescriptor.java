package oshajava.spec.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

public class ObjectTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
	public static final ObjectTypeDescriptor OBJECT = new ObjectTypeDescriptor(CanonicalName.of("java.lang.Object"));
	
	protected final CanonicalName typeName;
//	protected final List<TypeDescriptor> typeParameters;
	
	protected ObjectTypeDescriptor(CanonicalName typeName) {
		this.typeName = typeName;
//		this.typeParameters = Collections.emptyList();
	}
//	public ObjectTypeDescriptor(CanonicalName typeName, List<TypeDescriptor> typeParameters) {
//		this.typeName = typeName;
////		this.typeParameters = typeParameters;
//	}
//	public ObjectTypeDescriptor(DeclaredType type, Elements util) {
//		TypeElement e = (TypeElement)type.asElement();
//		typeName = CanonicalName.of(e, util);
////		this.typeParameters = new ArrayList<TypeDescriptor>();
////		for (TypeParameterElement v : e.getTypeParameters()) {
////			typeParameters.add(new TypeParameterDescriptor(v, util));
////		}
//	}
	
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

