package oshajava.runtime.exceptions;


public class InlinedEntryPointException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3164522535647062060L;
	
	public InlinedEntryPointException() {
		super("A thread entry point was inlined. Please annotate it with a non-inlined policy.");
	}
}
