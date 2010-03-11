package oshajava.runtime.exceptions;

import oshajava.instrument.InstrumentationAgent;
import oshajava.runtime.RuntimeMonitor;
import oshajava.runtime.State;
import oshajava.runtime.ThreadState;



public abstract class IllegalCommunicationException extends OshaRuntimeException {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = -8360898879626150853L;
	
	protected final State writer, reader;
	protected final StackTraceElement[] trace;
	protected final String on;
	
	protected IllegalCommunicationException(final State writer, final State reader) {
		this(writer,reader,null,null);
	}
	
	protected IllegalCommunicationException(final State writer, final State reader, final StackTraceElement[] trace) {
		this(writer,reader,trace,null);
	}
	
	protected IllegalCommunicationException(final State writer, final State reader, final StackTraceElement[] trace, final String on) {
		this.writer = writer;
		this.reader = reader;
		this.trace = trace;
		if (on == null) {
		    this.on = null;
		} else {
    		this.on = InstrumentationAgent.sourceName(on);
		}
		RuntimeMonitor.fudgeTrace(this);
	}
	
	protected abstract String actionString();

	@Override
	public String getMessage() {
	    String out = "================================================================================"; 
	    	
	    out += (on != null ? "on " + on : "" ) + "\n" + reader + "\n" + actionString() + "\n" + writer;
	    
		if (trace != null) {
		    if (trace.length > 0) {
    		    out += "\nOriginator's stack trace:";
    		    for (StackTraceElement element : trace) {
    		        out += "\n" + element.toString();
    		    }
    	    } else {
    	        out += "\nOriginated at field initialization.";
    	    }
		}
	    out += "================================================================================"; 

		return out;
	}

}
