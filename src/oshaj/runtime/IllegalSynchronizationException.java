package oshaj.runtime;

public class IllegalSynchronizationException extends
		IllegalCommunicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1736616437563200866L;

	public IllegalSynchronizationException(Thread writerThread, String writerMethod,
			Thread readerThread, String readerMethod) {
		super(writerThread, writerMethod, readerThread, readerMethod);
	}
	
	@Override
	protected String actionString() {
		return "acquired a lock last released by";
	}

}
