package oshajava.util.intset;

import java.io.Serializable;

import oshajava.util.ArrayUtil;
import oshajava.util.count.MaxRecorder;

/**
 * A set of ints represented by a bit vector.
 * 
 * TODO If many set sizes are actually < 32 or < 64, create an IntSet32 
 * or IntSet64 using int or long directly. 
 * 
 * @author bpw
 *
 */
public class BitVectorIntSet extends IntSet implements Serializable {
	
	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = 2152032957464344617L;
	
	/**
	 * Number of bits per slot (width of primitive type).
	 */
	protected static final int SLOT_SIZE = 32;
	
	public static final MaxRecorder maxSlots = new MaxRecorder();
	public static final boolean COUNT_SLOTS = true;

	/**
	 * The bit vector.
	 */
	protected int[] bits;
	
	/**
	 * Create a new set with initialCapacity.
	 * @param initialCapacity
	 */
	public BitVectorIntSet(final int initialCapacity) {		
		bits = new int[initialCapacity >= SLOT_SIZE ? initialCapacity/SLOT_SIZE : 1];
		if (COUNT_SLOTS) maxSlots.add(initialCapacity >= SLOT_SIZE ? initialCapacity/SLOT_SIZE : 1);
	}
	
	/**
	 * Create a new set of initial capacity SLOT_SIZE.
	 */
	public BitVectorIntSet() {
		this(SLOT_SIZE);
	}
	
	/**
	 * Add an int to the set.
	 * TODO not thread safe! Requires external synchronization!
	 */
	public void add(final int member) {
		final int slot = member / SLOT_SIZE;
		// resize if necessary.
		if (slot > bits.length) {
			bits = ArrayUtil.copy(bits, slot + 1);
			if (COUNT_SLOTS) maxSlots.add(slot + 1);
		}
		bits[slot] |= 1 << (member % SLOT_SIZE);
	}
	
	/**
	 * Remove any excess space, using the minimum space to represent the
	 * current set.
	 */
	public void fit() {
		int newBitsSize = bits[bits.length - 1];
		while (newBitsSize >= 0 && bits[newBitsSize] == 0) {
			newBitsSize--;
		}
		bits = ArrayUtil.copy(bits, newBitsSize);
	}

	/**
	 * Check if the set contains member.
	 */
	public boolean contains(final int member) {
		final int slot = member / SLOT_SIZE;
		if (slot > bits.length) {
			return false;
		}
		return (bits[slot] & (1 << (member % SLOT_SIZE))) != 0;
	}
	
	/**
	 * Check if the set contains all members of other.
	 * @param other
	 * @return
	 */
	public boolean containsAll(final BitVectorIntSet other) {
		int min, max;
		int[] longer;
		final int[] otherbits = other.bits;
		if (bits.length < otherbits.length) {
			min = bits.length;
			max = otherbits.length;
			longer = otherbits;
		} else {
			min = otherbits.length;
			max = bits.length;
			longer = bits;
		}
		for (int i = 0; i < min; i++) {
			if ((bits[i] | otherbits[i]) != bits[i]) {
				return false;
			}
		}
		for (int i = min; i < max; i++) {
			if (longer[i] != 0) return false;
		}
		return true;
	}
	
	public String toString() {
		String s = "";
		for (int i = 0; i <= bits.length * SLOT_SIZE; i++) {
			if (contains(i)) {
				if (s.length() != 0) {
					s += ", ";
				}
				s += i;
			}
		}
		return "{" + s + "}";
	}
	
	/**
	 * Check if the set is empty. O(bits.length)
	 */
	public boolean isEmpty() {
		for (int i : bits) {
			if (i > 0) return true;
		}
		return false;
	}
}
