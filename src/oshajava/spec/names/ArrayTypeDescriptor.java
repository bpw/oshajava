package oshajava.spec.names;

public class ArrayTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
//	public static final ArrayTypeDescriptor STRING_ARRAY = new ArrayTypeDescriptor(TypeDescriptor.ofClass("java.lang.String"), 1);
	
	protected final TypeDescriptor elementType;
	protected final int arrayDepth;
	
	@Override
	public int hashCode() {
		return elementType.hashCode() ^ arrayDepth;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == this) return true;
		return other instanceof ArrayTypeDescriptor && 
			elementType.equals(((ArrayTypeDescriptor)other).elementType) && arrayDepth == ((ArrayTypeDescriptor)other).arrayDepth;
	}
	
	protected ArrayTypeDescriptor(TypeDescriptor elementType, int arrayDepth) {
		this.elementType = elementType;
		this.arrayDepth = arrayDepth;
	}
	
	private String sourceBrackets() {
		String out = "";
		for (int i = 0; i < arrayDepth; i++) {
			out += "[]";
		}
		return out;
	}
	
	private String internalBrackets() {
		String out = "";
		for (int i = 0; i < arrayDepth; i++) {
			out += "[";
		}
		return out;
	}
	
	public String getSourceName() {
		return elementType.getSourceName() + sourceBrackets();
	}

	public String getInternalName() {
		return elementType.getInternalName() + sourceBrackets();
	}
	
	public String getSourceDescriptor() {
		return getSourceName();
	}
	
	public String getInternalDescriptor() {
		return internalBrackets() + elementType.getInternalDescriptor();
	}
}
