package oshaj.runtime;

import oshaj.util.IntSet;

public class LockState extends State {
	
	protected int depth = 0;

	protected LockState(Thread writerThread, int writerMethod) {
		super(writerThread, writerMethod);
	}

	protected LockState(Thread writerThread, int writerMethod, IntSet readerSet) {
		super(writerThread, writerMethod, readerSet);
	}

}
