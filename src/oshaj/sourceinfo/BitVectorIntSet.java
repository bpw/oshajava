package oshaj.sourceinfo;

import java.io.Serializable;

public class BitVectorIntSet extends IntSet implements Serializable {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = 2152032957464344617L;
	
	protected static final int SLOT_SIZE = 32;
	protected int[] bits;
	protected int maxBitIndex;
	
	public BitVectorIntSet(final int nbits) {
		bits = new int[nbits];
		maxBitIndex = nbits - 1;
	}
	
	public BitVectorIntSet() {
		this(SLOT_SIZE);
	}
	
	// TODO not thread safe! Requires external synchronization!
	public void add(final int bitIndex) {
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
	
	public boolean contains(int bitIndex) {
		if (bitIndex > maxBitIndex) {
			return false;
		}
		return (bits[bitIndex / SLOT_SIZE] & (1 << (bitIndex % SLOT_SIZE))) != 0;
	}
	
	public int[] toArray() {
		return bits;
	}
	
	public String toString() {
		String s = "";
		for (int i = 0; i <= maxBitIndex; i++) {
			if (contains(i)) {
				if (s.length() != 0) {
					s += ", ";
				}
				s += i;
			}
		}
		return "{" + s + "}";
	}
}
