package oshajava.spec.names;

import java.io.Serializable;

public abstract class Name implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * @return The source format String for the name.
	 */
	public abstract String toSourceString();
	
	/**
	 * @return The internal format String for the name.
	 */
	public abstract String toInternalString();
	
	/**
	 * @return The source format String for the name.
	 */
	@Override
	public String toString() {
		return toSourceString();
	}
	
	@Override
	public abstract int hashCode();
	
	@Override
	public abstract boolean equals(Object other);
	
}
