package oshajava.sourceinfo;

import java.io.Serializable;

public class Graph implements Serializable {
	
	private static final long serialVersionUID = 1L;

	private int nextID = 0;

	protected IntSet[] table;
	
	public Graph(int nodeCapacity) {
		table = new IntSet[nodeCapacity];
	}
	
	public final IntSet getOutEdges(int i) {
		assert i < nextID && i >= 0;
		return table[i];
	}
	
	public final boolean containsEdge(int i, int j) {
		assert i >= 0 && i < nextID && j >= 0 && j < nextID;
		final IntSet set = table[i];
		return set != null && set.contains(j);
	}
	
	public int add(IntSet set) {
		assert nextID >= table.length;
		final int id = nextID;
		++nextID;
		table[id] = set;
		return id;
	}
	
	public int capacity() {
		return table.length;
	}
	
	public int size() {
		return nextID;
	}
	
	protected final void resize(int n) {
		assert n > 0;
		IntSet[] p = new IntSet[n];
		System.arraycopy(table, 0, p, 0, n > nextID ? nextID : n);
		table = p;
	}
	
}
