package oshajava.runtime;

import java.io.IOException;
import java.lang.reflect.Array;

import oshajava.runtime.exceptions.IllegalCommunicationException;
import oshajava.runtime.exceptions.IllegalSharingException;
import oshajava.runtime.exceptions.IllegalSynchronizationException;
import oshajava.sourceinfo.Graph;
import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.util.GraphMLWriter;
import oshajava.util.WeakConcurrentIdentityHashMap;
import oshajava.util.count.Counter;
import oshajava.util.intset.BitVectorIntSet;

/**
 * TODO Possible optimizations:
 *  
 * 2. Don't call the enter and exit hooks for non-inlined methods that:
 *    - do not contain field accesses (except private reads);
 *    - do not contain any synchronization (including being a synchronized method);
 *    - do not contain any method calls to inlined methods.
 *    
 *    The last one (no inlined method calls) is thwarted a bit by the current
 *    dynamic spec-reading scheme.  Moving to static spec-reading would allow us
 *    to take advantage of that, but for now we have to assume any method that
 *    does not already have an id could be inlined.  Hence:
 *    
 * 3. Do a static pass to build the spec, at least on the classes that are
 *    immediately accessible.  (We'll always need dynamic spec reading, etc. to
 *    handle reflection and dynamic class loading, but for most apps we'll be able
 *    to get most of what's interesting up front.)
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
 * TODO Things to fix or add.
 * 
 * + Copy (partial) of java.*
 * 
 * + command line options
 * 
 * + Graph recording as annotation inference? i.e. insert annotations in the bytecode?
 * 
 * + Static spec reading
 * 
 * + Separate tool/pass to take a spec in some other format and the bytecode of an
 *   un@annotated program and insert the proper annotations in the bytecode.
 *   
 * + Separate tool/pass to take annotated bytecode and build/dump a MethodRegistry.
 * 
 * + measure performance difference of how readerSet is loaded in hooks.
 * 
 * + @Default annotation on class tells default annotation to use on unannotated methods. 
 * 
 * + way to annotate clinit
 * 
 * + see javax.annotation.processing and apt if you want source-level...
 * 
 * @author bpw
 */
public class RuntimeMonitor {

	// temporary graph-collection hack.
	protected static final Graph executionGraph = new Graph(1024);

	public static final boolean RECORD = false;
	
	/**
	 * Record profiling information.
	 */
	public static final boolean PROFILE = true;

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

	public static final boolean COUNT_ARRAY_CACHE = RuntimeMonitor.PROFILE && true;
	private static final Counter arrayCacheHits = new Counter();
	private static final Counter arrayCacheMisses = new Counter();
		
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
	public static void checkReadSlowPath(final State write, final State read) {
		if (!read.stack.checkWriter(write.stack)) {
			throw new IllegalSharingException(write, read);
		}
	}
	private static void checkRead(final State write, final ThreadState reader, final BitVectorIntSet wCache) {
		if (write.thread != reader && !wCache.contains(write.getStackID())) checkReadSlowPath(write, reader.state);
	}

	// TODO Skip the wCache parameter and just do the field lookup if needed?
	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void arrayRead(final Object array, final int index, final ThreadState reader, final BitVectorIntSet wCache) {
		final State[] cachedStates = reader.getCachedArrayStateArray(array);
		if (cachedStates != null) {
			if (COUNT_ARRAY_CACHE) arrayCacheHits.inc();
			checkRead(cachedStates[index], reader, wCache);
		} else {
			if (COUNT_ARRAY_CACHE) arrayCacheMisses.inc();
			final State[] states;
			try {
				states = arrayStates.get(array);
			} catch (NullPointerException e) {
				throw fudgeTrace(e);
			}
			if (states != null) {
				final State write = states[index];
				if (write != null) {
					checkRead(write, reader, wCache);
				}
				reader.cacheArrayStateArray(array, states);
			}
		}
	}

	public static void arrayWrite(final Object array, final int index, final State currentState, final ThreadState writer) {
		final State[] cachedStates = writer.getCachedArrayStateArray(array);
		if (cachedStates != null) {
			if (COUNT_ARRAY_CACHE) arrayCacheHits.inc();
			cachedStates[index] = currentState;
		} else {
			if (COUNT_ARRAY_CACHE) arrayCacheMisses.inc();
			State[] states = arrayStates.get(array);
			if (states == null) {
				// if array == null, we don't want to do anything more.
				// the user code will throw the NullPointerException.
				// by returning here, we also prevent the null key from
				// getting into arrayStates and causing false commmunication
				// ("collisions...")
				// Since this is the init case, we can afford to pay. ;-)
				if (array == null) {
					return;
				}
				states = new State[Array.getLength(array)];
				State[] old = arrayStates.putIfAbsent(array, states);
				if (old != null) {
					states = old;
				}
			}
			states[index] = currentState;
			writer.cacheArrayStateArray(array, states);
		}
	}

	// TODO cached write array and cached read array? or is the linkage the key?
	// TODO Skip the wCache parameter and just do the field lookup if needed?
	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void coarseArrayRead(final Object array, final ThreadState reader, final BitVectorIntSet wCache) {
		final State cachedState = reader.getCachedArrayState(array);
		if (cachedState != null) {
			if (COUNT_ARRAY_CACHE) arrayCacheHits.inc();
			checkRead(cachedState, reader, wCache);
		} else {
			if (COUNT_ARRAY_CACHE) arrayCacheMisses.inc();
			final Ref<State> stateRef;
			try {
				stateRef = coarseArrayStates.get(array);
			} catch (NullPointerException e) {
				throw fudgeTrace(e);
			}
			if (stateRef == null) {
				return;
			}
			checkRead(stateRef.contents, reader, wCache);
			reader.cacheArrayStateRef(array, stateRef);
		}
	}

	public static void coarseArrayWrite(final Object array, final State currentState, final ThreadState threadState) {
		//  if no array state caching, push the null check back to bytecode.
		final Ref<State> cachedRef = threadState.getCachedArrayStateRef(array);
		if (cachedRef != null) {
			if (COUNT_ARRAY_CACHE) arrayCacheHits.inc();
			cachedRef.contents = currentState;
		} else {
			if (COUNT_ARRAY_CACHE) arrayCacheMisses.inc();
			Ref<State> ref = coarseArrayStates.get(array);
			if (ref == null) {
				ref = new Ref<State>();
				ref.contents = currentState;
				// FIXME other threads could see this Ref with null contents unless CHM locks for insert.
				final Ref<State> oldRef = coarseArrayStates.putIfAbsent(array, ref);
				if (oldRef != null) {
					ref = oldRef;
					ref.contents = currentState;
				}
			}
			threadState.cacheArrayStateRef(array, ref);
		}
	}


	/**
	 * Lock release hook.  Since things are well-scoped in Java, we'll just do all the work
	 * in acquire.  Only need to hit the reentrancy counter here.
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
			final LockState ls = lockStates.get(lock);
    	    ls.decrementDepth();
			final int depth = ls.getDepth();
			if (depth < 1) {
				if (depth < 0) {
					Util.fail("Bad lock scoping");
				} else {
					holder.popLock();
				}
			}
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
			LockState lockState = holder.getLockState(lock);
			if (lockState != null) {
				// if it's in that stack, then it's already been acquired, so just increment the depth.
				lockState.incrementDepth();
			} else {
				// else look it up.
				lockState = lockStates.get(lock);
				if (lockState == null) {
					lockState = new LockState(holder.state);
					lockState.setDepth(1);
					// push it in the holder's cache.
					holder.pushLock(lock, lockState);
					lockState = lockStates.putIfAbsent(lock, lockState);
					Util.assertTrue(lockState == null);
				} else if (lockState.getDepth() < 0) {
					Util.fail("Bad lock scoping.");
				} else if (lockState.getDepth() == 0) {
					// First (non-reentrant) acquire by this thread.
					// NOTE: this is atomic, because we hold lock and no other thread can call
					// the acquire or release hooks until they hold the lock.
					final State lastHolderState = lockState.lastHolder;
					if (lastHolderState != holderState) {
						lockState.lastHolder = holderState;
						lockState.incrementDepth();
						if (lastHolderState.thread != holder) {
							// TODO pass writerCache as param
							if (!holderState.stack.writerCache.contains(lastHolderState.getStackID()) && !holderState.stack.checkWriter(lockState.lastHolder.stack)) {
								throw new IllegalSynchronizationException(lastHolderState, holderState);
							}

						}
					} else {
						lockState.incrementDepth();
					}
				    
					// push it in the holder's cache.
					holder.pushLock(lock, lockState);
				} else {
					// if we're already reentrant, just go one deeper.
					lockState.incrementDepth();
				}
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
		LockState lockState = holder.getLockState(lock);
		if (lockState == null) {
			lockState = lockStates.get(lock);
			Util.assertTrue(lockState != null && lockState.getDepth() > 0, "Bad prewait");
		}
		final int depth = lockState.getDepth();
		lockState.setDepth(0);
		return depth;
	}
	
	// TODO other granularity version.
	/**
	 * Hook to call when returning from a call to wait.
	 */
	public static void postwait(final Object lock, final int resumeDepth, final ThreadState ts, final State currentState) {
		LockState lockState = ts.getLockState(lock);
		if (lockState == null) {
			lockState = lockStates.get(lock);
			Util.assertTrue(lockState != null && lockState.getDepth() == 0, "Bad postwait");
		}
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
	 * Hook to call on non-inlined method exit.
	 * @param ts
	 */
	public static void exit(final ThreadState ts) {
		ts.exit();
	}

	private static <T extends Throwable> T fudgeTrace(T t) {
		return t;
		//		StackTraceElement[] stack = t.getStackTrace();
		//		int i = 0;
		//		while (i < stack.length && (stack[i].getClassName().startsWith(RuntimeMonitor.class.getPackage().getName())
		//				|| stack[i].getClassName().startsWith("acme."))) {
		//			i++;
		//		}
		//		if (i > 0) {
		//			StackTraceElement[] fudgedStack = new StackTraceElement[stack.length - i];
		//			System.arraycopy(stack, i, fudgedStack, 0, stack.length - i);
		//			t.setStackTrace(fudgedStack);
		//		}
		//		return t;
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
			Util.log("Set RuntimeMonitor.PROFILE to false to disable profiling (and speed the tool up!)");
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
			if (COUNT_ARRAY_CACHE) {
				Util.logf("Array cache hits: %d", arrayCacheHits.value());
				Util.logf("Array cache misses: %d", arrayCacheMisses.value());
				int totalHitWalk = 0;
				for (int i = 0; i < ThreadState.CACHED_ARRAYS; i++) {
					Util.logf("Array cache hits of length %d: %d", i+1, ThreadState.hitLengths[i].value());
					totalHitWalk += ThreadState.hitLengths[i].value() * (i+1);
				}
				Util.logf("Array cache hit rate: %f, average walk to hit: %f", 
						(float)arrayCacheHits.value() / (float)(arrayCacheHits.value() + arrayCacheMisses.value()),
						(float)totalHitWalk / (float)arrayCacheHits.value());
			}
		}
	}
	

}
