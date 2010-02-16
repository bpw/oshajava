package oshajava.runtime.exceptions;

import oshajava.runtime.State;

public class IllegalSharingException extends IllegalCommunicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3447182487366275642L;

	public IllegalSharingException(final State writer, final State reader) {
		super(writer, reader);
	}

	@Override
	protected String actionString() {
		return "read a value written by";
	}

}
