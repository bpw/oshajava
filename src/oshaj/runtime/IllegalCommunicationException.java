package oshaj.runtime;


public abstract class IllegalCommunicationException extends RuntimeException {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = -8360898879626150853L;
	
	protected final String writerMethod, readerMethod;
	protected final Thread writerThread, readerThread;
	
	protected IllegalCommunicationException(Thread writerThread, String writerMethod, 
			Thread readerThread, String readerMethod) {
		this.writerMethod = writerMethod;
		this.readerMethod = readerMethod;
		this.writerThread = writerThread;
		this.readerThread = readerThread;
	}
	
	protected abstract String actionString();

	@Override
	public String getMessage() {
		return String.format(
				"\"%s\" (thread id=%d) in %s %s \"%s\" (thread id=%d) in %s.", 
				readerThread.getName(), readerThread.getId(), readerMethod,
				actionString(),
				writerThread.getName(), writerThread.getId(), writerMethod
		);
	}

}
