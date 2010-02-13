package oshajava.util.intset;

import oshajava.util.ArrayUtil;

public class ImmutableBitVectorIntSet extends IntSet {

	final int[] set;
	final int[] bitvector;

	public ImmutableBitVectorIntSet(final BitVectorIntSet s) {
		int size = 0;
		int[] tmpset = new int[(s.bits.length / 4) + 1];
		for (int i = 0; i <= s.bits.length * BitVectorIntSet.SLOT_SIZE; i++) {
			if (s.contains(i)) {
				tmpset[size++] = i;
			}
		}
		
		set = ArrayUtil.copy(tmpset, size);
	}
	
	public void add(int member) {
		if (!super.contains(member)) {
			if (size == set.length) {
				set = ArrayUtil.copy(set, size*2);
			}
			set[size++] = member;
		}
		super.add(member);
	}
	
	public void fit() {
		set = ArrayUtil.copy(set, size);
		super.fit();
	}
	
	public int[] toIntArray() {
		return set;
	}
}
