package oshajava.spec;

import java.io.Serializable;

public abstract class SpecFile implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private final CanonicalName name;
	
	public SpecFile(final CanonicalName name) {
		this.name = name;
	}
	
	/**
	 * Returns the module's canonical name.
	 * @return
	 */
	public CanonicalName getName() {
		return name;
	}
	
}
