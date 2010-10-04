package oshajava.spec.names;

import java.io.Serializable;

public abstract class Name implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * @return The source format String for the name.
	 */
	public abstract String getSourceName();
	
	/**
	 * @return The internal format String for the name.
	 */
	public abstract String getInternalName();
	
	/**
	 * @return The source format String for the name.
	 */
	@Override
	public String toString() {
		return getSourceName();
	}
	
	@Override
	public abstract int hashCode();
	
	@Override
	public abstract boolean equals(Object other);
	
}
