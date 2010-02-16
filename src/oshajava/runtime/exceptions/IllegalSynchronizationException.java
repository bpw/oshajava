package oshajava.runtime.exceptions;

import oshajava.runtime.State;

public class IllegalSynchronizationException extends
		IllegalCommunicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1736616437563200866L;

	public IllegalSynchronizationException(final State writer, final State reader) {
		super(writer, reader);
	}
	
	@Override
	protected String actionString() {
		return "acquired a lock last released by";
	}

}
