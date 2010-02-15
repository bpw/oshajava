package oshajava.sourceinfo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;

import oshajava.support.acme.util.Util;
import oshajava.util.intset.BitVectorIntSet;
import oshajava.util.intset.IntSet;

/**
 * A graph representation targeted at method communication graphs.
 * 
 * @author bpw
 *
 */
public class Graph implements Serializable {
	
	private static final long serialVersionUID = 1L;

	/**
	 * ID counter for adding new nodes.
	 */
	private int nextID = 0;

	/**
	 * Array of IntSets. Rows represent edge sources and columns represent 
	 * edge destinations.
	 */
	protected BitVectorIntSet[] table;
	
	/**
	 * Construct a graph with an initial node capacity.
	 * 
	 * @param nodeCapacity
	 */
	public Graph(int nodeCapacity) {
		table = new BitVectorIntSet[nodeCapacity];
	}
	
	/**
	 * Get the set of destination nodes to which the given source node is connected.
	 * @param i
	 * @return
	 */
	public final BitVectorIntSet getOutEdges(int i) {
		assert i < nextID && i >= 0;
		return table[i];
	}
	
	/**
	 * Get the set of source nodes to which the given destination node is connected.
	 * This is SLOW, since the representation is optimized for following directed edges 
	 * "the right way".
	 * @param i
	 * @return
	 */
	public final boolean hasInEdges(int i) {
		for (IntSet set : table) {
			if (set.contains(i)) return true;
		}
		return false;
	}
	
	/**
	 * Check if the graph contains an edge from source node i to destination node j.
	 * @param i
	 * @param j
	 * @return
	 */
	public final boolean containsEdge(int i, int j) {
		assert i >= 0 && i < nextID && j >= 0 && j < nextID;
		final IntSet set = table[i];
		return set != null && set.contains(j);
	}
	
	/**
	 * Add a node that has edges to the nodes in the given int set.
	 * @param set
	 * @return
	 */
	public int add(BitVectorIntSet set) {
		assert nextID >= table.length;
		final int id = nextID;
		++nextID;
		table[id] = set;
		return id;
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
