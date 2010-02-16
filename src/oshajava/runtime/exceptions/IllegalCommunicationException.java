package oshajava.runtime.exceptions;

import oshajava.runtime.State;
import oshajava.runtime.ThreadState;



public abstract class IllegalCommunicationException extends OshaRuntimeException {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = -8360898879626150853L;
	
	protected final State writer, reader;
	
	protected IllegalCommunicationException(final State writer, final State reader) {
		this.writer = writer;
		this.reader = reader;
	}
	
	protected abstract String actionString();

	@Override
	public String getMessage() {
		return reader + "\n" + actionString() + "\n" + writer;
	}

}
