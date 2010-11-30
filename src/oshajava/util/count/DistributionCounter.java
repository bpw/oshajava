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

package oshajava.util.count;

import java.util.HashMap;
import java.util.Map;

import oshajava.util.Py;

public class DistributionCounter extends RangeRecorder {
	private final HashMap<Integer,Integer> dist = new HashMap<Integer,Integer>();
	private Distribution distrib = new Distribution();
	
	public DistributionCounter(final String desc) {
		super(desc);
	}
	
	public synchronized void add(int i) {
		super.add(i);
		if(dist.containsKey(i)) {
			dist.put(i, dist.get(i) + 1);
		} else {
			dist.put(i, 1);
		}
	}
	
	public synchronized float amean() {
		int sum = 0, n = 0;
		for (Map.Entry<Integer, Integer> e : dist.entrySet()) {
			sum += e.getKey() * e.getValue();
			n += e.getValue();
		}
		return (float)sum / (float)n;
	}
	
	public HashMap<Integer,Integer> getDist() {
		return dist;
	}
	
	class Distribution extends RangeRecorder.Range {
		public String toString() {
			return String.format("mean: %f, min: %d, max: %d", amean(), getMin(), getMax());
		}
	}
	
	public Distribution value() {
		return distrib;
	}
	
	public String valueToPy() {
		return Py.repr(dist);
	}
	
}
