package oshajava.spec.names;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

public class WildcardParameterDescriptor extends TypeDescriptor {

	private static final long serialVersionUID = 1L;
	
	private final List<TypeDescriptor> extendees;
	
	public WildcardParameterDescriptor(TypeParameterElement e, Elements util) {
//		this.varName = e.getSimpleName().toString();
		this.extendees = new ArrayList<TypeDescriptor>();
		for (TypeMirror t : e.getBounds()) {
			extendees.add(TypeDescriptor.of(t, util));
		}
	}

	@Override
	public String toSourceString() {
		return "?"; //varName + " extends " + extendees;
	}

	@Override
	public String toInternalString() {
		return "Ljava/lang/Object;";
	}

}
