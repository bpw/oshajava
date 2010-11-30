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

import java.lang.ref.WeakReference;

import oshajava.runtime.RuntimeMonitor.Ref;
import oshajava.support.acme.util.Assert;
import oshajava.util.cache.DirectMappedShadowCache;
import oshajava.util.cache.ShadowCache;
import oshajava.util.count.Counter;


/**
 * State information associated with a thread. ThreadStates represent 
 * threads and their metadata and store the current call stacks and 
 * communication state, in addition to caching lock and array states
 * recently accessed by the thread to avoid expensive HashMap lookups
 * where possible.
 * 
 * NOTE: All non-final fields are for thread private access only.
 * 
 * @author bpw
 *
 */
public final class ThreadState {
	
	/**
	 * Create a new ThreadState.
	 * @param thread
	 * @param stateTableSize
	 */
	public ThreadState(final Thread thread) {
		threadRef = new WeakReference<Thread>(thread);
		name = thread.getName();
		id = newID();
	}

	// -- Thread IDs -------------------------------------------------
	
	/**
	 * Counter for thread ids.
	 */
	private static int idCounter = -1;
	
	/**
	 * Get a new thread id.
	 * @return
	 */
	private static synchronized int newID() {
		Assert.assertTrue(idCounter < Integer.MAX_VALUE, "Ran out of thread IDs.");
		return ++idCounter;
	}
	
	/**
	 * Get the last thread id allocated.
	 * @return
	 */
	public static synchronized int lastID() {
		return idCounter;
	}
	
	/**
	 * Thread id.
	 */
	public final int id;
	
	// -- Thread metadata --------------------------------------------------------
	
	/**
	 * Let GC go as planned... We have refs to ThreadStates in ThreadLocals in the
	 * RuntimeMonitor, but also in States, which are as long-lived as their data.
	 */
	private final WeakReference<Thread> threadRef;
	
	/**
	 * The initial name of the Thread. (Used on getName, toString only when the
	 * Thread has already been GCed. Otherwise, we use the up-to-date name from
	 * t.getName().)
	 */
	private final String name;
	
	/**
	 * Get the name of the Thread this ThreadState represents.
	 * @return
	 */
	public String getName() {
		final Thread thread = threadRef.get();
		return thread == null ? "[No longer live. Originally named " + name + "]" : thread.getName();
	}
	
	public String toString() {
		return "Thread " + id + " (\"" + getName() + "\")";
	}
	
	// -- Thread call stack/state -----------------------------------------------------
	
	/**
	 * Cached copy of this thread's current State.
	 */
	public State state = State.root(this);
	
	/**
	 * Update call stack/state to reflect entering the method with id mid.
	 * @param methodUID
	 */
	protected void enter(final int methodUID) {
		state = state.call(methodUID);
	}
	
	/**
	 * Update call stack/state to reflect entering a class initializer.
	 */
	protected void enterClinit() {
		state = state.callClinit();
	}
	
	/**
	 * Update call stack/state to reflect exiting the method with id mid.
	 */
	protected void exit() {
		state = state.ret();
	}
	
	// -- Array state caching --------------------------------------------------------
	
	public static final Counter ARRAY_HITS = new Counter("Array hits"), ARRAY_MISSES = new Counter("Array misses");
	
	protected final ShadowCache<Object,Ref<State>> arrayStateCache = 
		Config.arrayTrackingOption.get() == Config.Granularity.FINE ? null :
			new DirectMappedShadowCache<Object,Ref<State>>(RuntimeMonitor.coarseArrayStates, 
					Config.arrayCacheSizeOption.get(),	ARRAY_HITS, ARRAY_MISSES);
	
	protected final ShadowCache<Object,State[]> arrayIndexStateCache = 
		Config.arrayTrackingOption.get() == Config.Granularity.FINE ?
				new DirectMappedShadowCache<Object,State[]>(RuntimeMonitor.arrayStates, 
						Config.arrayCacheSizeOption.get(), ARRAY_HITS, ARRAY_MISSES)
				: null;
	
//	/**
//	 * Direct-mapped cache -----------------------------------------------------------
//	 * Most in upper 90%s with 16. SOR at 88, Series at 50, Sparse at 72
//	 * 
//	 * LEGACY
//	 * Fully associative cache -------------------------------------------------------
//	 * For reference. Hit rates and avg successful walk lengths for given cache sizes.
//	 * 						History size
//	 * Benchmark			1		2		3		4		5		6		7		8
//	 * 
//	 * Crypt A				86	1.0	96	1.9	100	1.9	100	2.8
//	 * LUFact A				33	1.0	97	1.5	98	2.5	98	3.3
//	 * MolDyn A				89	1.0	90	2.0	91	3.0	91	4.0
//	 * RayTracer A			97	1.0	100	2.0	100	1.6	100 2.9
//	 * SOR A				57	1.0	66	1.9	66	2.9	97	2.8
//	 * Series A				0	1.0	50	1.5	50	2.5	50	3.5			50	5.5
//	 * SparseMatmult A		0	1.0	0.6	1.5	15	1.1	15	1.6	16	2.6	99	3.6
//	 * 
//	 * With cursor repositioning on read hits:
//	 * Crypt A						96	1.1	96	1.2	96	1.3
//	 * LUFact A						98	1.7	98	2.0	98	2.3
//	 * MolDyn A						91	1.0 92	1.1	93	1.1
//	 * RayTracer A					100	1.0	100	1.0	100	1.0
//	 * SOR A						66	1.2	86	1.7	91	1.8
//	 * Series A						50	2.0	50	2.5	50	3.0
//	 * SparseMatmult A				0.6	1.7	15	1.1	15	1.2			89	2.7
//	 * 
//	 * Also for reference, the alternate (non-cached) path consists of roughly 10 field
//	 * reads on its fast path in WeakConcurrentIdentityHashMap.  
//	 * So we should be able to optimize the tradeoff. E.g. we want to choose cache size N
//	 * to minimize
//	 *     ((1 - HitRate(N)) * (N + 10)) + (HitRate(N) * AvgHitTime(N)) 
//	 */
//	// TODO Do some GC of the cache or use array of WeakRefs to the actual array objects.
//	// TODO victim buffer or 2-way associative?

	// -- Lock state caching ---------------------------------------------------------
	
	public static final Counter LOCK_HITS = new Counter("Lock hits"), LOCK_MISSES = new Counter("Lock misses");

	protected final ShadowCache<Object,LockState> lockStateCache = 
		new DirectMappedShadowCache<Object,LockState>(RuntimeMonitor.lockStates, 
				Config.lockCacheSizeOption.get(), LOCK_HITS, LOCK_MISSES);	
}
