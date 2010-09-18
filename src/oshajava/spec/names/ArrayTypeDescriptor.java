package oshajava.spec.names;

public class ArrayTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
	public static final ArrayTypeDescriptor STRING_ARRAY = new ArrayTypeDescriptor(ObjectTypeDescriptor.of(CanonicalName.of("java.lang.String")), 1);
	
	protected final TypeDescriptor elementType;
	protected final int arrayDepth;
	
	@Override
	public int hashCode() {
		return elementType.hashCode() ^ arrayDepth;
	}
	
	@Override
	public boolean equals(Object other) {
		return other != null && other instanceof ArrayTypeDescriptor && 
			elementType.equals(((ArrayTypeDescriptor)other).elementType) && arrayDepth == ((ArrayTypeDescriptor)other).arrayDepth;
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
