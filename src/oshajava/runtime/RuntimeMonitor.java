package oshajava.runtime;

import java.io.IOException;
import java.lang.reflect.Array;

import oshajava.runtime.exceptions.IllegalCommunicationException;
import oshajava.runtime.exceptions.IllegalSharingException;
import oshajava.runtime.exceptions.IllegalSynchronizationException;
import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.util.GraphMLWriter;
import oshajava.util.WeakConcurrentIdentityHashMap;
import oshajava.util.cache.DirectMappedShadowCache;
import oshajava.util.intset.BitVectorIntSet;

/**
 * TODO Possible optimizations:
 *  
 * 2. Don't call the enter and exit hooks for non-inlined methods that:
 *    - do not contain field accesses (except private reads);
 *    - do not contain any synchronization (including being a synchronized method);
 *    - do not contain any method calls to inlined methods.
 *    
 * 9. When code is stable, customize and diversify array read/writer hooks.
 * 
 * 12. Choose array granularity on some level other than "yes or no for all."  Asin coarse for
 *     some, fine for others.
 *     
 * 13. Encode state (w/o reader set) as an int 
 *     low bits for method, high for thread... 12 bits for tid, 20 for mid. each thread
 *     has an array of reader sets... 
 *     
 *    
 * TODO Things to fix, add, or consider.
 * 
 * + Copy (partial) of java.*
 * 
 * + Graph recording as annotation inference? i.e. insert annotations in the bytecode?
 * 
 * @author bpw
 */
public class RuntimeMonitor {

	// temporary graph-collection hack.
//	protected static final Graph executionGraph = new Graph(1024);

	public static final boolean RECORD = false;
	
	/**
	 * Record profiling information.
	 */
	public static final boolean PROFILE = Config.profileOption.get();

	private static final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>() {
		@Override
		public ThreadState initialValue() {
			return new ThreadState(Thread.currentThread());
		}
	};

	// TODO test the WCIHM implementation to make sure it actually works and isn't just dropping
	// all the keys or something weird.
	protected static final WeakConcurrentIdentityHashMap<Object,LockState> lockStates = 
		new WeakConcurrentIdentityHashMap<Object,LockState>();
	protected static final WeakConcurrentIdentityHashMap<Object,State[]> arrayStates = 
		new WeakConcurrentIdentityHashMap<Object,State[]>();
	protected static final WeakConcurrentIdentityHashMap<Object,Ref<State>> coarseArrayStates = 
		new WeakConcurrentIdentityHashMap<Object,Ref<State>>();

	static class Ref<T> {
		T contents;
	}

	/*******************************************************************/

	//	public static void newArray(int length, Object array) {
	//		final State[] states = new State[length];
	//		for (int i = 0; i < length; i++) {
	//			// initially, accept anything.
	//			states[i] = new State(null, -1, UniversalIntSet.set);
	//		}
	//		arrayStates.put(array, states);
	//	}

	//	public static void newMultiArray(final Object array, final int dims) {
	//		int length = Array.getLength(array);
	//		newArray(length, array);
	//		if (dims > 0) {
	//			for (int i = 0; i < length; i++) {
	//				newMultiArray(Array.get(array, i), dims - 1);
	//			}
	//		}
	//	}


	// -- Checking ---------------------------------------------------------------------

	/**
	 * Checks a read by a shared reading method, i.e. a method that has in-edges
	 * and can read a write by the same thread or a write in one of the
	 * expectedReaders methods.
	 * 
	 * Slow path for reads.
	 * 
	 * @param state
	 * @param readerMethod
	 */
	public static void checkReadSlowPath(final State write, final State read, final StackTraceElement[] trace) {
		if (!(read.stack == Stack.classInitializer || write.stack == Stack.classInitializer) && !read.stack.checkWriter(write.stack)) {
			throw new IllegalSharingException(write, read, trace);
		}
	}
	private static void checkRead(final State write, final ThreadState reader, final BitVectorIntSet wCache, final StackTraceElement[] trace) {
		if (write.thread != reader && !wCache.contains(write.getStackID())) checkReadSlowPath(write, reader.state, trace);
	}

	// TODO Skip the wCache parameter and just do the field lookup if needed?
	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void arrayRead(final Object array, final int index, final ThreadState reader, final BitVectorIntSet wCache) {
		final State[] states;
		try {
			states = reader.arrayIndexStateCache.get(array);
		} catch (NullPointerException e) {
			throw fudgeTrace(e);
		}
		if (states != null) {
			final State write = states[index];
			if (write != null) {
				checkRead(write, reader, wCache, null);
			}
		}
	}

	public static void arrayWrite(final Object array, final int index, final State currentState, final ThreadState writer) {
		State[] states = writer.arrayIndexStateCache.get(array);
		if (states == null) {
			states = new State[Array.getLength(array)];
			final State[] oldStates = writer.arrayIndexStateCache.putIfAbsent(array, states);
			if (oldStates != null) {
				states = oldStates;
			}
		}
		states[index] = currentState;
	}

	// TODO cached write array and cached read array? or is the linkage the key?
	// TODO Skip the wCache parameter and just do the field lookup if needed?
	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void coarseArrayRead(final Object array, final ThreadState reader, final BitVectorIntSet wCache) {
		final Ref<State> stateRef = reader.arrayStateCache.get(array);
		if (stateRef != null) {
			checkRead(stateRef.contents, reader, wCache, null);
		}
	}

	public static void coarseArrayWrite(final Object array, final State currentState, final ThreadState threadState) {
		//  if no array state caching, push the null check back to bytecode.
		Ref<State> stateRef = threadState.arrayStateCache.get(array);
		if (stateRef != null) {
			stateRef.contents = currentState;
		} else {
			stateRef = new Ref<State>();
			stateRef.contents = currentState;
			stateRef = threadState.arrayStateCache.putIfAbsent(array, stateRef);
			if (stateRef != null) {
				stateRef.contents = currentState;
			}
		}
	}


	/**
	 * Lock release hook.  Since things are well-scoped in Java, we'll just do all the work
	 * in acquire.  The only thinw we need to do here is hit the reentrancy counter.
	 * 
	 * INVARIANT: The method instrumentor relies on the fact that release() does NOT throw
	 * ANY EXCEPTIONS.  We ensure the exit and release hooks are called on method exit
	 * as needed by surrounding the body after the enter and acquire hooks with a try {}
	 * catch (Throwable t) {}, where the catch block calls release and exit...
	 * 
	 * @param lock
	 */
	public static void release(final Object lock, final ThreadState holder) {
		try {
			final LockState ls = holder.lockStateCache.get(lock);
			Util.assertTrue(ls.getDepth() >= 0, "Bad lock scoping");
    	    ls.decrementDepth();
		} catch (Exception t) {
			Util.fail(t);
		}
	}
	public static void release(final ObjectWithState lock, final ThreadState holder) {
	    lock.__osha_lock_state.decrementDepth();
		final int depth = lock.__osha_lock_state.getDepth();
		if (depth < 0) Util.fail("Bad lock scoping");
	}

	/**
	 * Lock acquire hook.
	 * 
	 * @param nextMethods
	 * @param holderMethod
	 * @param lock
	 */
	public static void acquire(final Object lock, final ThreadState holder, final State holderState) {
		try {
			// get the lock state
			final LockState lockState = holder.lockStateCache.get(lock);
			if (lockState == null) {
				final LockState ls = new LockState(holder.state);
				ls.setDepth(1);
				Util.assertTrue(
						holder.lockStateCache.putIfAbsent(lock, ls) == null);					
				return;
			}
			
			// check and update the lock state.
			if (lockState.getDepth() < 0) {
				Util.fail("Bad lock scoping.");
			} else if (lockState.getDepth() == 0) {
				// First (non-reentrant) acquire by this thread.
				// NOTE: this is atomic, because we hold lock and no other thread can call
				// the acquire or release hooks until they hold the lock.
				final State lastHolderState = lockState.lastHolder;
				// if the last holder was in the current state:
				if (lastHolderState == holderState) {
					// no check needed, just increment depth to 1.
					lockState.incrementDepth();
				} else { // if the last holder was in a different state than the current state:
					// set the lock state's holder to us.
					lockState.lastHolder = holderState;
					// increment depth to 1
					lockState.incrementDepth();
					// if last holder was not the same thread
					if (lastHolderState.thread != holder) {
						// if communication is not allowed, throw an exception.
						if (!holderState.stack.writerCache.contains(lastHolderState.getStackID()) && !holderState.stack.checkWriter(lockState.lastHolder.stack)) {
							throw new IllegalSynchronizationException(lastHolderState, holderState);
						}
					}
				}
			} else { // depth is > 0. This is a reentrant acquire
				lockState.incrementDepth();
			}

		} catch (IllegalCommunicationException e) {
			throw e;
		} catch (Throwable t) {
			Util.fail(t);
		}
	}
//	public static void acquire(final ObjectWithState lock, final ThreadState holder, final State holderState) {
//		LockState ls = lock.__osha_lock_state;
//		if (ls == null) {
//			ls = new LockState(holderState);
//			ls.setDepth(1);
//			lock.__osha_lock_state = ls;
//		} else if (ls.getDepth() < 0) {
//			Util.fail("Bad lock scoping.");
//		} else if (ls.getDepth() == 0) {
//			// First (non-reentrant) acquire by this thread.
//			// NOTE: this is atomic, because we hold lock and no other thread can call
//			// the acquire or release hooks until they hold the lock.
//			final State lastHolderState = ls.lastHolder;
//			if (lastHolderState != holderState) {
//				ls.lastHolder = holderState;
//				ls.incrementDepth();
//				if (RECORD) {
//					recordEdge(lastHolderState.stack.method, holderState.stack.method);
//				}
//				if (lastHolderState != null && lastHolderState.thread != holder) {
//					if (checkStacks(lastHolderState.stack, holderState.stack)) {
//						throw new IllegalSynchronizationException(
//								lastHolderState.thread, "FIXME", 
//								holder, "FIXME"
//						);
//					}
//				}
//
//			} else {
//				ls.incrementDepth();
//			}
//		} else {
//			// if we're already reentrant, just go one deeper.
//			ls.incrementDepth();
//		}
//	}
//	
	// TODO other granularity version.
	/**
	 * Hook to call before making a call to wait.
	 */
	public static int prewait(final Object lock, final ThreadState holder) {
		final LockState lockState = holder.lockStateCache.get(lock);
		Util.assertTrue(lockState.getDepth() > 0, "Bad prewait");
		final int depth = lockState.getDepth();
		lockState.setDepth(0);
		return depth;
	}
	
	// TODO other granularity version.
	/**
	 * Hook to call when returning from a call to wait.
	 */
	public static void postwait(final Object lock, final int resumeDepth, final ThreadState ts, final State currentState) {
		final LockState lockState = ts.lockStateCache.get(lock);
		Util.assertTrue(lockState.getDepth() == 0, "Bad postwait");
		lockState.setDepth(resumeDepth);
		final State lastHolderState = lockState.lastHolder;
		if (lastHolderState != currentState) { // necessary because of the timeout versions of wait.
			lockState.lastHolder = currentState;
			// No thread check needed here. If last lastHolderState != currentState then last thread != this thread
			// TODO pass writerCache as param
			if (!currentState.stack.writerCache.contains(lastHolderState.getStackID()) 
					&& !currentState.stack.checkWriter(lastHolderState.stack)) {
				throw new IllegalSynchronizationException(lastHolderState, currentState);
			}
		}
	}


	// -- Recording --------------------------------------------------------------------

//	private static void record(final Stack writer, final Stack reader) {
//		// FIXME repeat stack traversal from Spec.isAllowed to record all edges.
//		// Or just record in Spec.isAllowed.
//		// for each edge call recordEdge.
//	}
//	
//	private static void recordEdge(int src, int dest) {
//		((BitVectorIntSet)executionGraph.getOutEdges(src)).add(dest);
//	}

	// -- General ----------------------------------------------------------------------


	/**
	 * Hook to get the current ThreadState.
	 */
	public static ThreadState getThreadState() {
		return threadState.get();
	}

	public static State getCurrentState() {
		return threadState.get().state;
	}

	/**
	 * Hook to call on non-inlined method entry.
	 * 
	 * @param methodUID
	 * @return the ThreadState for the current thread.
	 */
	public static ThreadState enter(final int methodUID) {
		final ThreadState ts = threadState.get();
		ts.enter(methodUID);
		return ts;
	}
	
	/**
	 * Hook to call when entering class initializer.
	 * 
	 * @return the ThreadState for the current thread.
	 */
	public static ThreadState enterClinit() {
		final ThreadState ts = threadState.get();
		ts.enterClinit();
		return ts;
	}

	/**
	 * Hook to call on non-inlined method exit.
	 * @param ts
	 */
	public static void exit(final ThreadState ts) {
		ts.exit();
	}

	private static <T extends Throwable> T fudgeTrace(T t) {
		if (Config.fudgeExceptionTracesOption.get()) {
				StackTraceElement[] stack = t.getStackTrace();
				int i = 0;
				while (i < stack.length && (stack[i].getClassName().startsWith(RuntimeMonitor.class.getPackage().getName()))) {
					i++;
				}
				if (i > 0) {
					StackTraceElement[] fudgedStack = new StackTraceElement[stack.length - i];
					System.arraycopy(stack, i, fudgedStack, 0, stack.length - i);
					t.setStackTrace(fudgedStack);
				}
		}
		return t;
	}

	/**
	 * Hook to call on program exit.
	 * 
	 * @param mainClass
	 */
	public static void fini(String mainClass) {
		if (RECORD) {
			try {
				final GraphMLWriter graphml = new GraphMLWriter(mainClass + ".oshajava.execution.graphml");
				//FIXME
				graphml.close();
			} catch (IOException e) {
				Util.log("Failed to dump execution graph due to IOException.");
			}
		}
		// Report some stats.
		if (PROFILE) {
			Util.log("---- Profile info ------------------------------------");
//			Util.log("Set RuntimeMonitor.PROFILE to false to disable profiling (and speed the tool up!)");
			Util.logf("Distinct threads created: %d", ThreadState.lastID() + 1);
			if (Stack.COUNT_STACKS) Util.logf("Distinct stacks created: %d", Stack.stacksCreated.value());
			Util.logf("Frequently communicating stacks: %d", Stack.lastID() + 1);
			if (State.COUNT_STATES) {
				Util.logf("Distinct states created: %d", State.statesCreated.value());
				Util.logf("Average duplication of stacks (truncated): %f", (float) State.statesCreated.value() / ((float)Stack.lastID() + 1));
			}
			if (BitVectorIntSet.COUNT_SLOTS) Util.logf("Max BitVectorIntSet slots: %d", BitVectorIntSet.maxSlots.value());
			if (ModuleSpec.COUNT_METHODS) Util.logf("Max non-inlined methods per module: %d", ModuleSpec.maxMethods.value());
			Util.logf("Modules loaded: %d", Spec.countModules());
			if (DirectMappedShadowCache.COUNT) {
				Util.logf("Array accesses: %d", ThreadState.ARRAY_HITS.value() + ThreadState.ARRAY_MISSES.value());
				Util.logf("    cache hits: %d", ThreadState.ARRAY_HITS.value());
				Util.logf("  cache misses: %d", ThreadState.ARRAY_MISSES.value());
				Util.logf("      hit rate: %f", 
						(float)ThreadState.ARRAY_HITS.value() / (float)(ThreadState.ARRAY_HITS.value() + ThreadState.ARRAY_MISSES.value()));
				Util.logf("    cache size: %d", Config.arrayCacheSizeOption.get());
				
				Util.logf(" Lock accesses: %d", ThreadState.LOCK_HITS.value() + ThreadState.LOCK_MISSES.value());
				Util.logf("    cache hits: %d", ThreadState.LOCK_HITS.value());
				Util.logf("  cache misses: %d", ThreadState.LOCK_MISSES.value());
				Util.logf("      hit rate: %f", 
						(float)ThreadState.LOCK_HITS.value() / (float)(ThreadState.LOCK_HITS.value() + ThreadState.LOCK_MISSES.value()));
				Util.logf("    cache size: %d", Config.lockCacheSizeOption.get());
//				int totalHitWalk = 0;
//				for (int i = 0; i < ThreadState.CACHED_ARRAYS; i++) {
//					Util.logf("Array cache hits of length %d: %d", i+1, ThreadState.hitLengths[i].value());
//					totalHitWalk += ThreadState.hitLengths[i].value() * (i+1);
//				}
//				Util.logf("Array cache hit rate: %f, average walk to hit: %f", 
//						(float)arrayCacheHits.value() / (float)(arrayCacheHits.value() + arrayCacheMisses.value()),
//						(float)totalHitWalk / (float)arrayCacheHits.value());
			}
		}
	}
	

}
