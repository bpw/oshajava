/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.util;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

import oshajava.runtime.RuntimeMonitor;
import oshajava.support.acme.util.Assert;
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
public class BitVectorIntSet implements Serializable, Iterable<Integer> {
	
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
			Assert.assertTrue(slot < MAX_SLOTS);
			bits = ArrayUtil.copy(bits, slot + 1);
			if (COUNT_SLOTS) maxSlots.add(slot + 1);
		}
		bits[slot] |= (1 << (member % SLOT_SIZE));
		Assert.assertTrue(bits[slot] != 0);
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
		int newBitsSize = bits[bits.length - 1]; // TODO Check this initialization.
												 //  I believe it should be bits.length.
		while (newBitsSize >= 0 && bits[newBitsSize] == 0) {
			newBitsSize--;
		}
		Assert.assertTrue(newBitsSize >= 0); // TODO This assertion can fail...
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
			if (i != 0) return false; // XXX Was '> 0' but bits could be shifted into the sign bit.
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
