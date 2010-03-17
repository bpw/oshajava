package oshajava.runtime;

import java.lang.reflect.Array;

import oshajava.runtime.exceptions.IllegalCommunicationException;
import oshajava.runtime.exceptions.IllegalSharingException;
import oshajava.runtime.exceptions.IllegalSynchronizationException;
import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.util.WeakConcurrentIdentityHashMap;
import oshajava.util.cache.DirectMappedShadowCache;
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

	
	/**
	 * Record profiling information.
	 */
	public static final boolean PROFILE = Config.profileOption.get();

	public static final Counter fieldReadCounter = new Counter("All field reads");
	public static final Counter fieldCommCounter = new Counter("Communicating field reads");
	public static final Counter fieldSlowPathCounter = new Counter("Communicating field read slow path");
	public static final Counter arrayReadCounter = new Counter("All array reads");
	public static final Counter arrayCommCounter = new Counter("Communicating array reads");
	public static final Counter arraySlowPathCounter = new Counter("Communicating array read slow path");
	public static final Counter lockCounter = new Counter("All acquires");
	public static final Counter lockCommCounter = new Counter("Communicating acquires");
	public static final Counter lockSlowPathCounter = new Counter("Communicating acquire slow path");


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
	public static void checkFieldRead(final State write, final State read, final String on) {
		if (PROFILE) {
			fieldSlowPathCounter.inc();
		}
		if (!read.stack.checkWriter(write.stack)) {
		    IllegalSharingException exc = new IllegalSharingException(write, read, null, on);
		    if (Config.failStopOption.get()) {
		        Util.fail(exc);
		    } else {
		        throw exc;
		    }
		}
	}
	public static void checkFieldRead(final State write, final State read, final String on, 
			final StackTraceElement[] trace) {
		if (PROFILE) {
			fieldSlowPathCounter.inc();
		}
		if (!read.stack.checkWriter(write.stack)) {
			IllegalSharingException exc = new IllegalSharingException(write, read, trace, on);
		    if (Config.failStopOption.get()) {
		        Util.fail(exc);
		    } else {
		        throw exc;
		    }
		}
	}
	private static void checkArrayRead(final State write, final ThreadState reader, final BitVectorIntSet wCache, final StackTraceElement[] trace) {
		if (write.thread != reader) {
			if (PROFILE) {
				arrayCommCounter.inc();
			}
			if (!wCache.contains(write.getStackID())) {
				if (PROFILE) {
					arraySlowPathCounter.inc();
				}
				if (!reader.state.stack.checkWriter(write.stack)) {
					IllegalSharingException exc = new IllegalSharingException(write, reader.state, trace);
		    		if (Config.failStopOption.get()) {
		        		Util.fail(exc);
		    		} else {
		        		throw exc;
		    		}
				}
			}
		}
	}

	// TODO Skip the wCache parameter and just do the field lookup if needed?
	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void arrayRead(final Object array, final int index, final ThreadState reader, final BitVectorIntSet wCache) {
		if (PROFILE) {
			arrayReadCounter.inc();
		}
		final State[] states;
		try {
			states = reader.arrayIndexStateCache.get(array);
		} catch (NullPointerException e) {
			throw fudgeTrace(e);
		}
		if (states != null) {
			final State write = states[index];
			if (write != null) {
				checkArrayRead(write, reader, wCache, null);
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
		if (PROFILE) {
			arrayReadCounter.inc();
		}
		final Ref<State> stateRef = reader.arrayStateCache.get(array);
		if (stateRef != null) {
			checkArrayRead(stateRef.contents, reader, wCache, null);
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
			lockCounter.inc();
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
						if (PROFILE) {
							lockCommCounter.inc();
						}
						// if communication is not allowed, throw an exception.
						if (!holderState.stack.writerCache.contains(lastHolderState.getStackID())) {
							if (PROFILE) {
								lockSlowPathCounter.inc();
							}
							if (!holderState.stack.checkWriter(lockState.lastHolder.stack)) {
								throw new IllegalSynchronizationException(lastHolderState, holderState);
							}
						}
					}
				}
			} else { // depth is > 0. This is a reentrant acquire
				lockState.incrementDepth();
			}

		} catch (IllegalCommunicationException e) {
		    if (Config.failStopOption.get()) {
		        Util.fail(e);
		    } else {
		        throw e;
		    }
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
		if (PROFILE) {
			lockCounter.inc();
		}
		final LockState lockState = ts.lockStateCache.get(lock);
		Util.assertTrue(lockState.getDepth() == 0, "Bad postwait");
		lockState.setDepth(resumeDepth);
		final State lastHolderState = lockState.lastHolder;
		if (lastHolderState != currentState) { // necessary because of the timeout versions of wait.
			lockState.lastHolder = currentState;
			// No thread check needed here. If last lastHolderState != currentState then last thread != this thread
			if (PROFILE) {
				lockCommCounter.inc();
			}
			// TODO pass writerCache as param
			if (!currentState.stack.writerCache.contains(lastHolderState.getStackID())) {
				if (PROFILE) {
					lockSlowPathCounter.inc();
				}
				if (!currentState.stack.checkWriter(lastHolderState.stack)) {
					IllegalSynchronizationException exc = new IllegalSynchronizationException(lastHolderState, currentState);
    		    	if (Config.failStopOption.get()) {
    		        	Util.fail(exc);
    		    	} else {
    		        	throw exc;
    		    	}
				}
			}
		}
	}


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
	
	public static void countRead() {
		fieldReadCounter.inc();
	}
	public static void countComm() {
		fieldCommCounter.inc();
	}

	public static <T extends Throwable> T fudgeTrace(T t) {
		if (Config.fudgeExceptionTracesOption.get()) {
			Util.debugf("fudge", "Fudging. package name is %s", RuntimeMonitor.class.getPackage().getName());
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
		// Report some stats.
		if (PROFILE) {
			Util.log("---- Profile info ------------------------------------");
			Util.logf("Distinct threads created: %d", ThreadState.lastID() + 1);
			if (Stack.COUNT_STACKS) {
				Util.log(Stack.stacksCreated);
				Util.logf("Frequently communicating stacks: %d", Stack.lastID() + 1);
				Util.log(Stack.communicatingStackDepths);
				Util.log(Stack.readerStackDepths);
				Util.log(Stack.writerStackDepths);
				Util.log(Stack.stackDepthDiffs);
				Util.log(Stack.segCountDist);
				Util.log(Stack.setLengthDist);
				Util.log(Stack.segSizeDist);
				Util.log(Stack.modulesUsed);
				Util.log(Stack.stackWalks);
				Util.log(Stack.memo2Hits);
			}
			Util.log(fieldReadCounter);
			Util.log(fieldCommCounter);
			Util.log(fieldSlowPathCounter);
			Util.log(lockCounter);
			Util.log(lockCommCounter);
			Util.log(lockSlowPathCounter);
			Util.log(arrayReadCounter);
			Util.log(arrayCommCounter);
			Util.log(arraySlowPathCounter);
			Util.log("");
			double fr = (double)fieldReadCounter.value();
			Util.logf("Thread-local field reads:    %f%%", 
					(double)(fieldReadCounter.value() - fieldCommCounter.value()) / fr * 100.0);
			Util.logf("Comm. fast path field reads: %f%%", (double)(fieldCommCounter.value() - fieldSlowPathCounter.value()) / fr * 100.0);
			Util.logf("Comm. slow path field reads: %f%%", (double)fieldSlowPathCounter.value() / fr * 100.0);
			Util.log("");
			double ar = (double)arrayReadCounter.value();
			Util.logf("Thread-local array reads:    %f%%", 
					(double)(arrayReadCounter.value() - arrayCommCounter.value()) / ar * 100.0);
			Util.logf("Comm. fast path array reads: %f%%", (double)(arrayCommCounter.value() - arraySlowPathCounter.value()) / ar * 100.0);
			Util.logf("Comm. slow path array reads: %f%%", (double)arraySlowPathCounter.value() / ar * 100.0);
			Util.log("");
			double lr = (double)lockCounter.value();
			Util.logf("Thread-local lock acquires:    %f%%", 
					(double)(lockCounter.value() - lockCommCounter.value()) / lr * 100.0);
			Util.logf("Comm. fast path lock acquires: %f%%", (double)(lockCommCounter.value() - lockSlowPathCounter.value()) / lr * 100.0);
			Util.logf("Comm. slow path lock acquires: %f%%", (double)lockSlowPathCounter.value() / lr * 100.0);
			
			if (State.COUNT_STATES) {
				Util.log(State.statesCreated);
//				Util.logf("Average duplication of stacks would be: %f", (float) State.statesCreated.value() / ((float)Stack.lastID() + 1));
			}
			if (BitVectorIntSet.COUNT_SLOTS) {
				Util.log(BitVectorIntSet.maxSlots);
			}
			if (ModuleSpec.COUNT_METHODS) {
				Util.log(ModuleSpec.maxCommMethods);
				Util.log(ModuleSpec.maxInterfaceMethods);
				Util.log(ModuleSpec.maxMethods);
			}
			Util.logf("Modules loaded: %d", Spec.countModules());
			if (DirectMappedShadowCache.COUNT) {
				Util.logf("Array accesses: %d", ThreadState.ARRAY_HITS.value() + ThreadState.ARRAY_MISSES.value());
				Util.logf("    cache hits: %d", ThreadState.ARRAY_HITS.value());
				Util.logf("  cache misses: %d", ThreadState.ARRAY_MISSES.value());
				Util.logf("      hit rate: %f%%", 
						100.0 * (float)ThreadState.ARRAY_HITS.value() / (float)(ThreadState.ARRAY_HITS.value() + ThreadState.ARRAY_MISSES.value()));
				Util.logf("    cache size: %d", Config.arrayCacheSizeOption.get());
				
				Util.logf(" Lock accesses: %d", ThreadState.LOCK_HITS.value() + ThreadState.LOCK_MISSES.value());
				Util.logf("    cache hits: %d", ThreadState.LOCK_HITS.value());
				Util.logf("  cache misses: %d", ThreadState.LOCK_MISSES.value());
				Util.logf("      hit rate: %f%%", 
						100.0 * (float)ThreadState.LOCK_HITS.value() / (float)(ThreadState.LOCK_HITS.value() + ThreadState.LOCK_MISSES.value()));
				Util.logf("    cache size: %d", Config.lockCacheSizeOption.get());
			}
		}
		if (Stack.RECORD) Stack.dumpGraphs(mainClass);
	}
	

}
