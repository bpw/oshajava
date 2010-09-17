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
	public int hashCode() {
		return toString().hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		return toString().equals(other.toString());
	}
	
}
