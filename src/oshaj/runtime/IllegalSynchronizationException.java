package oshaj.runtime;

public class IllegalSynchronizationException extends
		IllegalCommunicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1736616437563200866L;

	public IllegalSynchronizationException(long writerTid, int writerMethod,
			long readerTid, int readerMethod) {
		super(writerTid, writerMethod, readerTid, readerMethod);
		// TODO Auto-generated constructor stub
	}

}
