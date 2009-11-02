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



final public class Timer extends AbstractCounter {

	private long totalTime;
	private int count = 0;
	
	public Timer(String group, String name) {
		super(group, name);
		totalTime = 0;
	}
	
	public Timer(String name) {
		this(null, name);
	}
	
	public final long start() {
		return System.nanoTime(); 
	}

	final synchronized public void stop() {
	}

	final synchronized public void stop(long startTime) {
		long endTime = System.nanoTime();
		totalTime += (endTime - startTime);
		count++;
	}

	@Override
	public String get() {
		long inc = 0;  // extra bits that haven't been committed yet...
		long b = 0;  // extra bits that haven't been committed yet...
		double totalTime = (this.totalTime + inc) / 1000000;
		if (count + b > 0) {
			return String.format("<total>%g</total> <count>%d</count> <ave>%g</ave>", totalTime, (count + b), totalTime / (count + b));
		} else {
			return String.format("<total>%g</total> <count>%d</count> ", totalTime, (count + b));
		}
	}
}


