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


public class ExpandableGraph extends Graph {
	
	private static final long serialVersionUID = 1L;
	/**
	 * ID counter for adding new nodes.
	 */
	private int nextID = 0;

	public ExpandableGraph() {
		this(8);
	}
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
