package oshajava.spec.names;


public class PrimitiveDescriptor extends TypeDescriptor {

	private static final long serialVersionUID = 1L;
	
	public static final PrimitiveDescriptor BOOLEAN = new PrimitiveDescriptor("boolean", "Z");
	public static final PrimitiveDescriptor BYTE    = new PrimitiveDescriptor("byte",    "B");
	public static final PrimitiveDescriptor CHAR    = new PrimitiveDescriptor("char",    "C");
	public static final PrimitiveDescriptor DOUBLE  = new PrimitiveDescriptor("double",  "D");
	public static final PrimitiveDescriptor FLOAT   = new PrimitiveDescriptor("float",   "F");
	public static final PrimitiveDescriptor INT     = new PrimitiveDescriptor("int",     "I");
	public static final PrimitiveDescriptor LONG    = new PrimitiveDescriptor("long",    "J");
	public static final PrimitiveDescriptor SHORT   = new PrimitiveDescriptor("short",   "S");
	public static final PrimitiveDescriptor VOID    = new PrimitiveDescriptor("void",    "V");
	
	private final String sourceDescriptor, internalDescriptor;
	
	/**
	 * Since the constructor is private, we check only against the internal descriptor...
	 */
	@Override
	public int hashCode() {
		return internalDescriptor.hashCode();
	}
	
	/**
	 * Since the constructor is private, we check only against the internal descriptor...
	 */
	@Override
	public boolean equals(Object other) {
		if (other == this) return true;
		return other instanceof PrimitiveDescriptor && internalDescriptor.equals(((PrimitiveDescriptor)other).internalDescriptor);
	}
	
	private PrimitiveDescriptor(String sourceDescriptor, String internalDescriptor) {
		this.internalDescriptor = internalDescriptor;
		this.sourceDescriptor = sourceDescriptor;
	}

	@Override
	public String getSourceName() {
		return sourceDescriptor;
	}

	@Override
	public String getInternalName() {
		return internalDescriptor;
	}

	@Override
	public String getSourceDescriptor() {
		return sourceDescriptor;
	}

	@Override
	public String getInternalDescriptor() {
		return internalDescriptor;
	}

}
