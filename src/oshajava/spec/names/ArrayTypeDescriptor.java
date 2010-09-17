package oshajava.spec.names;

public class ArrayTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
	protected final TypeDescriptor elementType;
	protected final int arrayDepth;
	
	public ArrayTypeDescriptor(TypeDescriptor elementType) {
		this(elementType, 0);
	}
	public ArrayTypeDescriptor(TypeDescriptor elementType, int arrayDepth) {
		this.elementType = elementType;
		this.arrayDepth = arrayDepth;
	}
	
	public String toSourceString() {
		String out = elementType.toSourceString();
		for (int i = 0; i < arrayDepth; i++) {
			out += "[]";
		}
		return out;
	}

	public String toInternalString() {
		String out = "";
		for (int i = 0; i < arrayDepth; i++) {
			out += "[";
		}
		out += elementType.toInternalString();
		return out;
	}
}
