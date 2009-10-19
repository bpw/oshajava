package oshaj.runtime;

public class IllegalSharingException extends IllegalCommunicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3447182487366275642L;

	public IllegalSharingException(ThreadState writerThread, String writerMethod,
			ThreadState readerThread, String readerMethod) {
		super(writerThread, writerMethod, readerThread, readerMethod);
	}

	@Override
	protected String actionString() {
		return "read a value written by";
	}

}
