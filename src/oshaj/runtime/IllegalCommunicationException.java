package oshaj.runtime;


public class IllegalCommunicationException extends RuntimeException {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = -8360898879626150853L;
	
	protected final int writerMethod, readerMethod;
	protected final long writerTid, readerTid;
	
	protected IllegalCommunicationException(long writerTid, int writerMethod, long readerTid, int readerMethod) {
		this.writerMethod = writerMethod;
		this.readerMethod = readerMethod;
		this.writerTid = writerTid;
		this.readerTid = readerTid;
	}
}
