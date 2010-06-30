package oshajava.util.intset;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

import oshajava.runtime.RuntimeMonitor;
import oshajava.support.acme.util.Util;
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
public class BitVectorIntSet extends IntSet implements Serializable, Iterable<Integer> {
	
	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = 2152032957464344617L;
	
	/**
	 * Number of bits per slot (width of primitive type).
	 */
	protected static final int SLOT_SIZE = 32;
	
	public static final MaxRecorder maxSlots = new MaxRecorder("Max BitVectorIntSet slots");
	public static final boolean COUNT_SLOTS = RuntimeMonitor.PROFILE && true;
	
	private static final int MAX_SLOTS = Integer.MAX_VALUE / SLOT_SIZE - 1;

	/**
	 * The bit vector.
	 */
	protected int[] bits;
	
	/**
	 * Create a new set with initialCapacity.
	 * @param initialCapacity
	 */
	public BitVectorIntSet(final int initialCapacity) {
		if (initialCapacity <= SLOT_SIZE) {
			bits = new int[1];
		} else if (initialCapacity % SLOT_SIZE == 0){
			bits = new int[initialCapacity/SLOT_SIZE];
		} else {
			bits = new int[initialCapacity/SLOT_SIZE + 1];
		}
		if (COUNT_SLOTS) maxSlots.add(bits.length);
	}
	
	/**
	 * Create a new set of initial capacity SLOT_SIZE.
	 */
	public BitVectorIntSet() {
		this(SLOT_SIZE);
	}
	
	/**
	 * Add an int to the set.
	 */
	public synchronized void add(final int member) {
		final int slot = member / SLOT_SIZE;
		// resize if necessary.
		if (slot >= bits.length) {
			Util.assertTrue(slot < MAX_SLOTS);
			bits = ArrayUtil.copy(bits, slot + 1);
			if (COUNT_SLOTS) maxSlots.add(slot + 1);
		}
		bits[slot] |= (1 << (member % SLOT_SIZE));
	}
	
	public synchronized void addAll(final BitVectorIntSet set) {
		if (bits.length < set.bits.length) {
			bits = ArrayUtil.copy(bits, set.bits.length);
		}
		for (int i = 0; i < set.bits.length; i++) {
			bits[i] |= set.bits[i];
		}
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
		if (slot >= bits.length) {
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
		int min;
		final int[] otherbits = other.bits;
		if (bits.length < otherbits.length) {
			min = bits.length;
			// Ensure the additional bits are zero (we contain none of them).
			for (int i = bits.length; i < otherbits.length; i++) {
    			if (otherbits[i] != 0) return false;
    		}
		} else {
			min = otherbits.length;
			// Our additional bits don't matter.
		}
		for (int i = 0; i < min; i++) {
			if ((bits[i] | otherbits[i]) != bits[i]) {
				return false;
			}
		}
		return true;
	}
	
	public String toString() {
		String s = "";
		for (int i = 0; i < bits.length * SLOT_SIZE; i++) {
			if (contains(i)) {
				if (s.length() != 0) {
					s += ", ";
				}
				s += i;
			}
		}
		return "{" + s + "}";
	}
	
	public Set<Integer> toJavaSet() {
	    Set<Integer> out = new HashSet<Integer>();
	    for (int i = 0; i < bits.length * SLOT_SIZE; i++) {
			if (contains(i)) {
				out.add(i);
			}
		}
	    return out;
	}
	
	/**
	 * Check if the set is empty. O(bits.length)
	 */
	public boolean isEmpty() {
		for (int i : bits) {
			if (i > 0) return false;
		}
		return true;
	}
	
	public int size() {
	    int count = 0;
	    for (int i : bits) {
	        count += Integer.bitCount(i);
	    }
	    return count;
	}
	
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>() {
			private int next = 0;
			public boolean hasNext() {
				while (next < bits.length * SLOT_SIZE) {
					if (contains(next)) return true;
					next++;
				}
				return false;
			}

			public Integer next() {
				return next++;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
