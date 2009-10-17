package oshaj.runtime;

import oshaj.util.IntSet;

public class LockState extends State {
	
	protected int depth = 0;

	protected LockState(long writerTid, int writerMethod) {
		super(writerTid, writerMethod);
	}

	protected LockState(long writerTid, int writerMethod, IntSet readerSet) {
		super(writerTid, writerMethod);
	}

}
