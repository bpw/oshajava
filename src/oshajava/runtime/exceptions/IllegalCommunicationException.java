/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.runtime.exceptions;

import oshajava.runtime.RuntimeMonitor;
import oshajava.runtime.State;



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
    		this.on = on;
		}
		RuntimeMonitor.fudgeTrace(this);
	}
	
	protected abstract String actionString();

	@Override
	public String getMessage() {
	    String out = "\n================================================================================"; 
	    	
	    out += "\nIllegal communication " + (on != null ? "on " + on : "" ) + "\n";
	    
	    out += "Last write:\n";
	    	
	    out += writer + "\n";
	    
		if (trace != null) {
		    if (trace.length > 0) {
    		    out += "\nLast writer stack trace:";
    		    for (StackTraceElement element : trace) {
    		        out += "\n" + element.toString();
    		    }
    	    } else {
    	        out += "\nLastWrite at field initialization.";
    	    }
		    out += "\n";
		}
		
		out += "\nCurrent read:\n";
		out += reader;
	    out += "\n================================================================================"; 

		return out;
	}

}
