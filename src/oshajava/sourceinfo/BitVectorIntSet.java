package oshajava.sourceinfo;

import java.io.Serializable;

/**
 * A set of ints represented by a bit vector.
 * @author bpw
 *
 */
public class BitVectorIntSet extends IntSet implements Serializable {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = 2152032957464344617L;
	
	protected static final int SLOT_SIZE = 32;
	protected int[] bits;
	protected int maxBitIndex;
	
	public BitVectorIntSet(final int nbits) {
		bits = new int[nbits/SLOT_SIZE];
		maxBitIndex = nbits - 1;
	}
	
	public BitVectorIntSet() {
		this(SLOT_SIZE);
	}
	
	// TODO not thread safe! Requires external synchronization!
	public void add(final int bitIndex) {
		if (bitIndex > maxBitIndex) {
			upsize(bitIndex+1);
		}
		bits[bitIndex / SLOT_SIZE] |= 1 << (bitIndex % SLOT_SIZE);
	}
	public synchronized void syncedAdd(final int bitIndex) {add(bitIndex);}
	
	protected void upsize(int nbits) {
		if (nbits < SLOT_SIZE) {
			nbits = SLOT_SIZE;
		}
		if (nbits < maxBitIndex) {
			return;
		}
		final int[] tmp = new int[(nbits % SLOT_SIZE == 0 ? nbits/SLOT_SIZE : nbits/SLOT_SIZE + 1)];
		System.arraycopy(bits, 0, tmp, 0, bits.length);
		maxBitIndex = tmp.length*SLOT_SIZE - 1;
		bits = tmp;
	}
	
	public boolean contains(final int bitIndex) {
		if (bitIndex > maxBitIndex) {
			return false;
		}
		return (bits[bitIndex / SLOT_SIZE] & (1 << (bitIndex % SLOT_SIZE))) != 0;
	}
	public synchronized boolean syncedContains(final int bitIndex) {return contains(bitIndex);}
	
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
	
	public boolean isEmpty() {
		for (int i : bits) {
			if (i > 0) return true;
		}
		return false;
	}
}
