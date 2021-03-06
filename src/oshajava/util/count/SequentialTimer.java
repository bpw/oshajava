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

public class SequentialTimer extends AbstractCounter<Long> {

	private static final long OFF = -1;
	private long elapsed = 0;
	private long lastStart = OFF;
	public SequentialTimer(String desc) {
		super(desc);
	}
	
	public synchronized void start() {
		assert lastStart == OFF;
		lastStart = System.currentTimeMillis();
	}
	
	public synchronized void stop() {
		long end = System.currentTimeMillis();
		assert lastStart != OFF;
		elapsed += end - lastStart;
		lastStart = OFF;
	}

	@Override
	public Long value() {
		return elapsed;
	}

	@Override
	public String valueToPy() {
		return elapsed + "";
	}

}
