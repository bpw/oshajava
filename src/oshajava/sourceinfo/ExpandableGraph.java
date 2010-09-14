package oshajava.sourceinfo;

import oshajava.util.intset.BitVectorIntSet;

public class ExpandableGraph extends Graph {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * ID counter for adding new nodes.
	 */
	private int nextID = 0;

	public ExpandableGraph(int initialNodeCapacity) {
		super(initialNodeCapacity, initialNodeCapacity);
	}
	
	/**
	 * Get the capacity of the table.
	 * @return
	 */
	public int capacity() {
		return table.length;
	}
	
	/**
	 * Get the number of nodes.
	 * @return
	 */
	public int size() {
		return nextID;
	}
	
	/****************/

	/**
	 * Add a node that has edges to the nodes in the given int set.
	 * @param set
	 * @return
	 */
	public int addNode(BitVectorIntSet set) {
		assert nextID >= table.length;
		final int id = nextID;
		++nextID;
		table[id] = set;
		return id;
	}
	
	public void setOutEdges(int i, BitVectorIntSet set) {
		assert i < table.length;
		table[i] = set;
	}
	
	public void addEdge(int i, int j) {
		getOutEdges(i).add(j);
	}
	
	/**
	 * Resize the internal table to hold up to n nodes.
	 * @param n
	 */
	protected final void resize(int n) {
		assert n > 0;
		BitVectorIntSet[] p = new BitVectorIntSet[n];
		System.arraycopy(table, 0, p, 0, n > nextID ? nextID : n);
		table = p;
	}
}
