package oshajava.runtime;

public class IllegalSynchronizationException extends
		IllegalCommunicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1736616437563200866L;

	public IllegalSynchronizationException(ThreadState writerThread, String writerMethod,
			ThreadState readerThread, String readerMethod) {
		super(writerThread, writerMethod, readerThread, readerMethod);
	}
	
	@Override
	protected String actionString() {
		return "acquired a lock last released by";
	}

}
