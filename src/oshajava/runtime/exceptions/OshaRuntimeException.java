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

package oshajava.runtime.exceptions;

import oshajava.runtime.Config;
import oshajava.runtime.RuntimeMonitor;

public abstract class OshaRuntimeException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2528906495574526086L;
	
	private static final String CANONICAL_NAME = RuntimeMonitor.class.getCanonicalName();
		
	public OshaRuntimeException() {
		if (Config.fudgeExceptionTracesOption.get()) {
			// fudge the stack to remove the top frame(s?) belonging to the RuntimeMonitor and make it
			// look like the VM threw the exception inside whatever user code method caused it.
			StackTraceElement[] stack = getStackTrace();
			int i = 0;
			while (i < stack.length && stack[i].getClassName().equals(CANONICAL_NAME)) {
				i++;
			}
			if (i > 0) {
				StackTraceElement[] fudgedStack = new StackTraceElement[stack.length - i];
				System.arraycopy(stack, i, fudgedStack, 0, stack.length - i);
				setStackTrace(fudgedStack);
			}
		}
	}
}
