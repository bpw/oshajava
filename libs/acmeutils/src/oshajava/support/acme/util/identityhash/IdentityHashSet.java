/******************************************************************************

Copyright (c) 2009, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

******************************************************************************/

/*
 * @(#)HashSet.java	1.19 00/02/02
 *
 * Copyright 1997-2000 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */

package oshajava.support.acme.util.identityhash;
import java.util.*;

/**
 * This class implements the <tt>Set</tt> interface, backed by a hash table
 * (actually a <tt>IdentityHashMap</tt> instance).  It makes no guarantees as to the
 * iteration order of the set; in particular, it does not guarantee that the
 * order will remain constant over time.  This class permits the <tt>null</tt>
 * element.<p>
 *
 * This class offers constant time performance for the basic operations
 * (<tt>add</tt>, <tt>remove</tt>, <tt>contains</tt> and <tt>size</tt>),
 * assuming the hash function disperses the elements properly among the
 * buckets.  Iterating over this set requires time proportional to the sum of
 * the <tt>HashSet</tt> instance's size (the number of elements) plus the
 * "capacity" of the backing <tt>IdentityHashMap</tt> instance (the number of
 * buckets).  Thus, it's very important not to set the intial capacity too
 * high (or the load factor too low) if iteration performance is important.<p>
 *
 * <b>Note that this implementation is not synchronized.</b> If multiple
 * threads access a set concurrently, and at least one of the threads modifies
 * the set, it <i>must</i> be synchronized externally.  This is typically
 * accomplished by synchronizing on some object that naturally encapsulates
 * the set.  If no such object exists, the set should be "wrapped" using the
 * <tt>Collections.synchronizedSet</tt> method.  This is best done at creation
 * time, to prevent accidental unsynchronized access to the <tt>HashSet</tt>
 * instance:
 * 
 * <pre>
 *     Set s = Collections.synchronizedSet(new HashSet(...));
 * </pre><p>
 *
 * The iterators returned by this class's <tt>iterator</tt> method are
 * <i>fail-fast</i>: if the set is modified at any time after the iterator is
 * created, in any way except through the iterator's own <tt>remove</tt>
 * method, the Iterator throws a <tt>ConcurrentModificationException</tt>.
 * Thus, in the face of concurrent modification, the iterator fails quickly
 * and cleanly, rather than risking arbitrary, non-deterministic behavior at
 * an undetermined time in the future.
 * 
 * @author  Josh Bloch
 * @version 1.19, 02/02/00
 * @see	    Collection
 * @see	    Set
 * @see	    TreeSet
 * @see	    Collections#synchronizedSet(Set)
 * @see	    IdentityHashMap
 * @since 1.2
 */

@SuppressWarnings("serial")
public class IdentityHashSet<T> extends AbstractSet<T>
implements Set<T>, Cloneable, java.io.Serializable
{
	private transient IdentityHashMap map;
	
	// Dummy value to associate with an Object in the backing Map
	private static final Object PRESENT = new Object();
	
	/**
	 * Constructs a new, empty set; the backing <tt>IdentityHashMap</tt> instance has
	 * default capacity and load factor, which is <tt>0.75</tt>.
	 */
	public IdentityHashSet() {
		map = new IdentityHashMap();
	}
	
	/**
	 * Constructs a new set containing the elements in the specified
	 * collection.  The capacity of the backing <tt>IdentityHashMap</tt> instance is
	 * twice the size of the specified collection or eleven (whichever is
	 * greater), and the default load factor (which is <tt>0.75</tt>) is used.
	 *
	 * @param c the collection whose elements are to be placed into this set.
	 */
	public IdentityHashSet(Collection c) {
		map = new IdentityHashMap(Math.max(2*c.size(), 11));
		addAll(c);
	}
	
	/**
	 * Constructs a new, empty set; the backing <tt>IdentityHashMap</tt> instance has
	 * the specified initial capacity and the specified load factor.
	 *
	 * @param      initialCapacity   the initial capacity of the hash map.
	 * @param      loadFactor        the load factor of the hash map.
	 * @throws     IllegalArgumentException if the initial capacity is less
	 *             than zero, or if the load factor is nonpositive.
	 */
	public IdentityHashSet(int initialCapacity, float loadFactor) {
		map = new IdentityHashMap(initialCapacity);
	}
	
	/**
	 * Constructs a new, empty set; the backing <tt>IdentityHashMap</tt> instance has
	 * the specified initial capacity and default load factor, which is
	 * <tt>0.75</tt>.
	 *
	 * @param      initialCapacity   the initial capacity of the hash table.
	 * @throws     IllegalArgumentException if the initial capacity is less
	 *             than zero.
	 */
	public IdentityHashSet(int initialCapacity) {
		map = new IdentityHashMap(initialCapacity);
	}
	
	/**
	 * Returns an iterator over the elements in this set.  The elements
	 * are returned in no particular order.
	 *
	 * @return an Iterator over the elements in this set.
	 * @see ConcurrentModificationException
	 */
	public Iterator iterator() {
		return map.keySet().iterator();
	}
	
	/**
	 * Returns the number of elements in this set (its cardinality).
	 *
	 * @return the number of elements in this set (its cardinality).
	 */
	public int size() {
		return map.size();
	}
	
	/**
	 * Returns <tt>true</tt> if this set contains no elements.
	 *
	 * @return <tt>true</tt> if this set contains no elements.
	 */
	public boolean isEmpty() {
		return map.isEmpty();
	}
	
	/**
	 * Returns <tt>true</tt> if this set contains the specified element.
	 *
	 * @param o element whose presence in this set is to be tested.
	 * @return <tt>true</tt> if this set contains the specified element.
	 */
	public boolean contains(Object o) {
		return map.containsKey(o);
	}
	
	/**
	 * Adds the specified element to this set if it is not already
	 * present.
	 *
	 * @param o element to be added to this set.
	 * @return <tt>true</tt> if the set did not already contain the specified
	 * element.
	 */
	public boolean add(Object o) {
		return map.put(o, PRESENT)==null;
	}
	
	/**
	 * Removes the given element from this set if it is present.
	 *
	 * @param o object to be removed from this set, if present.
	 * @return <tt>true</tt> if the set contained the specified element.
	 */
	public boolean remove(Object o) {
		return map.remove(o)==PRESENT;
	}
	
	/**
	 * Removes all of the elements from this set.
	 */
	public void clear() {
		map.clear();
	}
	
	/**
	 * Returns a shallow copy of this <tt>HashSet</tt> instance: the elements
	 * themselves are not cloned.
	 *
	 * @return a shallow copy of this set.
	 */
	public Object clone() {
		try { 
			IdentityHashSet newSet = (IdentityHashSet)super.clone();
			newSet.map = (IdentityHashMap)map.clone();
			return newSet;
		} catch (CloneNotSupportedException e) { 
			throw new InternalError();
		}
	}
	
	/**
	 * Save the state of this <tt>HashSet</tt> instance to a stream (that is,
	 * serialize this set).
	 *
	 * @serialData The capacity of the backing <tt>IdentityHashMap</tt> instance
	 *		   (int), and its load factor (float) are emitted, followed by
	 *		   the size of the set (the number of elements it contains)
	 *		   (int), followed by all of its elements (each an Object) in
	 *             no particular order.
	 */
	private synchronized void writeObject(java.io.ObjectOutputStream s)
	throws java.io.IOException {
		// Write out any hidden serialization magic
		s.defaultWriteObject();
				
		// Write out size
		s.writeInt(map.size());
		
		// Write out all elements in the proper order.
		for (Iterator i=map.keySet().iterator(); i.hasNext(); ) {
			s.writeObject(i.next());
		}
	}
	
	/**
	 * Reconstitute the <tt>HashSet</tt> instance from a stream (that is,
	 * deserialize it).
	 */
	private synchronized void readObject(java.io.ObjectInputStream s)
	throws java.io.IOException, ClassNotFoundException {
		// Read in any hidden serialization magic
		s.defaultReadObject();
		
		// Read in IdentityHashMap capacity and load factor and create backing IdentityHashMap
		int capacity = s.readInt();
		map = new IdentityHashMap(capacity);
		
		// Read in size
		int size = s.readInt();
		
		// Read in all elements in the proper order.
		for (int i=0; i<size; i++) {
			Object e = s.readObject();
			map.put(e, PRESENT);
		}
	}
}
