package oshajava.spec;

import java.io.Serializable;

public abstract class SpecFile implements Serializable {

	private static final long serialVersionUID = 1L;
	
	protected final String qualifiedName;
	
	public SpecFile(final String qualifiedName) {
		this.qualifiedName = qualifiedName;
	}
	
	/**
	 * Returns the module's qualified name.
	 * @return
	 */
	public String getName() {
		return qualifiedName;
	}
	
}
