package oshaj.runtime;

import oshaj.Spec;

public class IllegalCommunicationException extends RuntimeException {

	protected final int writerMethod, readerMethod;
	protected final long writerTid, readerTid;
	
	protected IllegalCommunicationException(long writerTid, int writerMethod, long readerTid, int readerMethod) {
		this.writerMethod = writerMethod;
		this.readerMethod = readerMethod;
		this.writerTid = writerTid;
		this.readerTid = readerTid;
	}
}
