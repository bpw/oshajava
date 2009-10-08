package oshaj.runtime;

import oshaj.Method;

public class UnexpectedCommunicationException extends Exception {
	// TODO store the reader and the writer.
	private final Method writerMethod, readerMethod;
	private final long writerTid, readerTid;
	
	protected UnexpectedCommunicationException(long writerTid, Method writerMethod, long readerTid, Method readerMethod) {
		this.writerMethod = writerMethod;
		this.readerMethod = readerMethod;
		this.writerTid = writerTid;
		this.readerTid = readerTid;
	}
}
