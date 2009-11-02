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

package oshajava.support.acme.util.collections;

import java.util.Iterator;

public class IterableArrayIterator<T> implements Iterator<T> {
	
	private ArrayIterator<Iterable<T>> csIter;
	private Iterator<T> current;
	private boolean empty = false;
	
	public IterableArrayIterator(Iterable<T> cs[]) {
		this.csIter = new ArrayIterator<Iterable<T>>(cs);
		advance();
	}
	
	public void advance() {
		if (csIter.hasNext()) {
			do {
				Iterable<T> coll = csIter.next();
				current = coll.iterator();
			} while (!current.hasNext() && csIter.hasNext());
		}
		if ((current == null || !current.hasNext()) && !csIter.hasNext()) {
			empty = true;
		}
	}
	
	public boolean hasNext() {
		return !empty;
	}
	
	public T next() {
		T t = current.next();
		if (!current.hasNext()) {
			advance();
		}
		return t;
	}
	
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
