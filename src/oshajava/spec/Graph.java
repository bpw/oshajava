package oshajava.spec;

import java.io.Serializable;
import java.util.Iterator;

import oshajava.util.intset.BitVectorIntSet;
import oshajava.util.intset.IntSet;

/**
 * A graph representation targeted at method communication graphs.
 * 
 * @author bpw
 *
 */
public class Graph implements Serializable, Iterable<Graph.Edge> {
	
	private static final long serialVersionUID = 1L;

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
		this(nodeCapacity, nodeCapacity);
	}
	public Graph(int nodeCapacity, int fillLine) {
		table = new BitVectorIntSet[nodeCapacity];
		if (fillLine > nodeCapacity) fillLine = nodeCapacity;
		for (int i = 0; i < fillLine; i++) {
			if (table[i] == null) {
				table[i] = new BitVectorIntSet();
			}
		}
	}
	
	/**
	 * Get the set of destination nodes to which the given source node is connected.
	 * @param i
	 * @return
	 */
	public final BitVectorIntSet getOutEdges(int i) {
		if (i >= size()) return null;
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
		assert i >= 0 && i < size() && j >= 0 && j < size();
		if (i >= size()) return false;
		final IntSet set = table[i];
		return set != null && set.contains(j);
	}
	
	
	/**
	 * Get the number of nodes.
	 * @return
	 */
	public int size() {
		return table.length;
	}
	
	public int numEdges() {
		int n = 0;
		for (BitVectorIntSet b : table) {
			if (b != null)  n += b.size();
		}
		return n;
	}

	public class Edge {
		public final int source, sink;
		public Edge(final int source, final int sink) {
			this.source = source;
			this.sink = sink;
		}
	}
	public Iterator<Edge> iterator() {
		return new Iterator<Edge>() {
			private int source = 0;
			private Iterator<Integer> sinks = null;

			public boolean hasNext() {
				if (sinks != null && sinks.hasNext()) {
					return true;
				}
				while (source < table.length) {
					if (sinks == null) {
						if (table[source] == null) {
							source++;
						} else {
							sinks = table[source].iterator();
						}
					} else if (!sinks.hasNext()) {
						source++;
						sinks = null;
					} else {
						return true;
					}
				}
				return false;
			}

			public Edge next() {
				return new Edge(source, sinks.next());
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};	
	}

}
