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

import java.io.Serializable;
import java.util.Iterator;

public class IndexedResourceMap<S> implements Serializable, Iterable<S> {

	protected S mapById[];
	protected int count = 0;

	public IndexedResourceMap(S[] bogusArray) {
		mapById = Arrays.copyOf(bogusArray, 128);
	}

	public int size() {
		return count;
	}
	
	public S get(final int id) {
		return mapById[id];
	}

	private void resize(final int n) {
		mapById = Arrays.copyOf(mapById, n);
	}

	public synchronized int add(S t) {
		final int id = count++;
		if (id >= mapById.length) {
			resize(id * 2);
		}
		mapById[id] = t;
		return id;
	}

	public Iterator<S> iterator() {
		return new ArrayIterator<S>(mapById, 0, count);
	}

	public String toString() {
		String result = "";
		for (S s : this) {
			result += "     " + s + "\n";
		}
		return result;
	}


}