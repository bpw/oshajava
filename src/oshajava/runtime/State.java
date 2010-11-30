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

package oshajava.runtime;

import java.util.HashMap;

import oshajava.util.count.Counter;


/**
 * A representation of communication context: thread x call stack.
 * 
 * @author bpw
 *
 */
public final class State {
	
	public static final Counter statesCreated = new Counter("States");
	public static final boolean COUNT_STATES = RuntimeMonitor.PROFILE && true;
		
	/**
	 * Thread id of the last thread to write to the field.
	 */
	public final ThreadState thread;
	
	/**
	 * Stack when the field was written.
	 */
	public final Stack stack;
	
	private int stackID = -1;
	
	/**
	 * State of caller.
	 */
	private final State caller;
	
	/**
	 * States for callees by method. Populated lazily.
	 */
	protected final HashMap<Integer,State> calleeToState = new HashMap<Integer,State>();
	
	private State(final ThreadState thread, final State caller, final Stack stack) {
		this.caller = caller;
		this.stack = stack;
		this.thread = thread;
		if (stack != null) this.stackID = stack.id;
		
		if (COUNT_STATES) statesCreated.inc();
	}
	
	/**
	 * Get the state resulting from calling method.
	 * @param methodUID
	 * @return
	 */
	public State call(final int methodUID) {
		State cs = calleeToState.get(methodUID);
		if (cs == null) {
			cs = new State(thread, this, Stack.push(methodUID, stack));
			calleeToState.put(methodUID, cs);
		}
		return cs;
	}
	
	/**
	 * Get the state resulting from invoking a class initializer.
	 * @param methodUID
	 * @return
	 */
	public State callClinit() {
		return new State(thread, this, Stack.classInitializer);
	}
	
	/**
	 * Get the state resulting from returning from the current method.
	 * @return
	 */
	public State ret() {
		return caller;
	}
	
	public int getStackID() {
		if (stackID == Integer.MAX_VALUE) {
			stackID = stack.id;
		}
		return stackID;
	}
	
	public String toString() {
		return thread.toString() + ":\n" + stack.toString();
	}
	
	/**
	 * Make the root state for a thread.
	 * @param ts
	 * @return
	 */
	public static State root(final ThreadState ts) {
		return new State(ts, null, Stack.root);
	}
}
