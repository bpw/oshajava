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
			table[i] = new BitVectorIntSet();
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
		for (BitVectorIntSet set : table) {
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
		final BitVectorIntSet set = table[i];
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
