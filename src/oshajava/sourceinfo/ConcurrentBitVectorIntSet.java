package oshajava.sourceinfo;

public class ConcurrentBitVectorIntSet extends BitVectorIntSet {
	protected Object[] locks;
	
	public ConcurrentBitVectorIntSet(int nbits) {
		super(nbits);
		locks = new Object[nbits/SLOT_SIZE];
		for (int i = 0; i < locks.length; i++) {
			locks[i] = new Object();
		}
	}
	public ConcurrentBitVectorIntSet() {
		this(SLOT_SIZE);
	}
	
	public void add(final int bitIndex) {
		if (bitIndex > maxBitIndex) {
			upsize(bitIndex);
		}
		bits[bitIndex / SLOT_SIZE] |= 1 << (bitIndex % SLOT_SIZE);
	}

}
