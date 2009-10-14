package oshaj.util;

public class BitVector {

	protected static final int SLOT_SIZE = 32;
	protected int[] bits;
	protected int maxBitIndex;
	
	public BitVector(final int nbits) {
		upsize(nbits);
	}
	
	public BitVector() {
		this(SLOT_SIZE);
	}
	
	public void set(final int bitIndex) {
		if (bitIndex > maxBitIndex) {
			upsize(bitIndex);
		}
		bits[bitIndex / SLOT_SIZE] |= 1 << (bitIndex % SLOT_SIZE);
	}
	
	protected void upsize(int nbits) {
		if (nbits < SLOT_SIZE) {
			nbits = SLOT_SIZE;
		}
		if (nbits < maxBitIndex) {
			return;
		}
		final long[] tmp = new long[(nbits % SLOT_SIZE == 0 ? nbits/SLOT_SIZE : nbits/SLOT_SIZE + 1)];
		for (int i = 0; i < bits.length; i++) {
			tmp[i] = bits[i];
		}
		maxBitIndex = nbits - 1;
	}
	
	public boolean get(int bitIndex) {
		if (bitIndex > maxBitIndex) {
			return false;
		}
		return (bits[bitIndex / SLOT_SIZE] & (1 << (bitIndex % SLOT_SIZE))) != 0;
	}
}
