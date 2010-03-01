package oshajava.runtime.exceptions;

import oshajava.runtime.State;
import oshajava.runtime.ThreadState;



public abstract class IllegalCommunicationException extends OshaRuntimeException {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = -8360898879626150853L;
	
	protected final State writer, reader;
	protected final StackTraceElement[] trace;
	
	protected IllegalCommunicationException(final State writer, final State reader) {
		this.writer = writer;
		this.reader = reader;
		this.trace = null;
	}
	
	protected IllegalCommunicationException(final State writer, final State reader, final StackTraceElement[] trace) {
		this.writer = writer;
		this.reader = reader;
		this.trace = trace;
	}
	
	protected abstract String actionString();

	@Override
	public String getMessage() {
	    String out = reader + "\n" + actionString() + "\n" + writer;
	    
		if (trace != null) {
		    out += "\nOriginator's stack trace:";
		    for (StackTraceElement element : trace) {
		        out += "\n" + element.toString();
		    }
		}
		
		return out;
	}

}
