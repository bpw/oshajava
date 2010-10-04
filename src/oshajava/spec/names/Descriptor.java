package oshajava.spec.names;


public abstract class Descriptor extends Name {

	private static final long serialVersionUID = 1L;

	public abstract String getSourceDescriptor();
	public abstract String getInternalDescriptor();
	
	@Override
	public String toString() {
		return getSourceDescriptor();
	}

}
