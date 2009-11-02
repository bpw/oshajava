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

package oshajava.support.acme.util.count;

import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;
import java.util.Map.Entry;

final public class FreqCounter<K extends Comparable<K>> extends AbstractCounter {

	HashMap<K,Long> map = new HashMap<K,Long>();
	
	public FreqCounter(String group, String name) {
		super(group, name);
	}
	
	public FreqCounter(String name) {
		this(null, name);
	}
	
	final public void inc(K key) {
		Long i = map.get(key); 
		if (i == null) i = 0L;
		map.put(key, i+1);
	}
	
	private long total() {
		long total = 0;
		for (Entry<K,Long> e : map.entrySet()) {
			total += e.getValue();
		}
		return total;
	}
	
	@Override
	public String get() {
		String result = "{ \n";
		long total = total();
		Vector<K> v = new Vector<K>(map.keySet());
		Collections.sort(v);
		for (K e : v) {
			long val = map.get(e);
			result += String.format("\t\t\t\t\t\t\t\t %-50s = %,12d (%2.2f%%)\n ", e, val, val / (double)total * 100);
		}
		return result.substring(0, result.length() - 1) + "\t\t\t\t\t\t\t\t\t\t}";
	}	
}
