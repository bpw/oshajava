package oshajava.runtime;

import java.io.IOException;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Set;

import oshajava.runtime.exceptions.IllegalCommunicationException;
import oshajava.runtime.exceptions.IllegalSharingException;
import oshajava.runtime.exceptions.IllegalSynchronizationException;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.option.Option;
import oshajava.util.BitVectorIntSet;
import oshajava.util.Py;
import oshajava.util.PyWriter;
import oshajava.util.WeakConcurrentIdentityHashMap;
import oshajava.util.cache.DirectMappedShadowCache;
import oshajava.util.count.AbstractCounter;
import oshajava.util.count.Counter;

/**
 * @author bpw
 */
public class RuntimeMonitor {

	
	/**
	 * Record profiling information.
	 */
	public static final boolean PROFILE = Config.profileOption.get() == Config.ProfileLevel.DEEP;
 	public static final boolean CREATE = Config.createOption.get();
 	public static final boolean INTRA_THREAD = Config.intraThreadOption.get();

	public static final Counter fieldReadCounter = new Counter("All field reads");
	public static final Counter fieldCommCounter = new Counter("Communicating field reads");
	public static final Counter fieldSlowPathCounter = new Counter("Communicating field read slow path");
	public static final Counter arrayReadCounter = new Counter("All array reads");
	public static final Counter arrayCommCounter = new Counter("Communicating array reads");
	public static final Counter arraySlowPathCounter = new Counter("Communicating array read slow path");
	public static final Counter lockCounter = new Counter("All acquires");
	public static final Counter lockCommCounter = new Counter("Communicating acquires");
	public static final Counter lockSlowPathCounter = new Counter("Communicating acquire slow path");
    public static final Set<String> createdGraphSet = new HashSet<String>();

	private static final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>() {
		@Override
		public ThreadState initialValue() {
			return new ThreadState(Thread.currentThread());
		}
	};

	// TODO Stress test the WCIHM.
	protected static final WeakConcurrentIdentityHashMap<Object,LockState> lockStates = 
		new WeakConcurrentIdentityHashMap<Object,LockState>(Config.shadowStoreGCoption.get());
	protected static final WeakConcurrentIdentityHashMap<Object,State[]> arrayStates = 
		new WeakConcurrentIdentityHashMap<Object,State[]>(Config.shadowStoreGCoption.get());
	protected static final WeakConcurrentIdentityHashMap<Object,Ref<State>> coarseArrayStates = 
		new WeakConcurrentIdentityHashMap<Object,Ref<State>>(Config.shadowStoreGCoption.get());

	static class Ref<T> {
		T contents; // FIXME make volatile if volatileShadows option is set...
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
		    error(new IllegalSharingException(write, read, null, on));
		}
	}
	public static void checkFieldRead(final State write, final State read, final String on, 
			final StackTraceElement[] trace) {
		if (PROFILE) {
			fieldSlowPathCounter.inc();
		}
		if (CREATE) {
		    createEdge(trace);
		}
		if (!read.stack.checkWriter(write.stack)) {
		    error(new IllegalSharingException(write, read, trace, on));
		}
	}
	private static void checkArrayRead(final State write, final ThreadState reader, final BitVectorIntSet wCache, final StackTraceElement[] trace) {
		if (INTRA_THREAD || write.thread != reader) {
			if (PROFILE) {
				arrayCommCounter.inc();
			}
			if (CREATE) {
			    createEdge(trace);
			}
			if (!wCache.contains(write.getStackID())) {
				if (PROFILE) {
					arraySlowPathCounter.inc();
				}
				if (!reader.state.stack.checkWriter(write.stack)) {
					error(new IllegalSharingException(write, reader.state, trace));
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
			Assert.assertTrue(ls.getDepth() >= 0, "Bad lock scoping");
    	    ls.decrementDepth();
		} catch (Exception t) {
			Assert.fail(t);
		}
	}
	public static void release(final ObjectWithState lock, final ThreadState holder) {
	    lock.__osha_lock_state.decrementDepth();
		final int depth = lock.__osha_lock_state.getDepth();
		if (depth < 0) Assert.fail("Bad lock scoping");
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
			if (PROFILE) {
                lockCounter.inc();
            }
			// get the lock state
			final LockState lockState = holder.lockStateCache.get(lock);
			if (lockState == null) {
				final LockState ls = new LockState(holder.state);
				ls.setDepth(1);
				Assert.assertTrue(
						holder.lockStateCache.putIfAbsent(lock, ls) == null);					
				return;
			}
			
			// check and update the lock state.
			if (lockState.getDepth() < 0) {
				Assert.fail("Bad lock scoping.");
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
					if (INTRA_THREAD || lastHolderState.thread != holder) {
						if (PROFILE) {
							lockCommCounter.inc();
						}
						
						// if communication is not allowed, throw an exception.
						if (!holderState.stack.writerCache.contains(lastHolderState.getStackID())) {
							if (PROFILE) {
								lockSlowPathCounter.inc();
							}
							if (!holderState.stack.checkWriter(lastHolderState.stack)) {
							    error(new IllegalSynchronizationException(lastHolderState, holderState));
							}
						}
					}
					
				}
			} else { // depth is > 0. This is a reentrant acquire
				lockState.incrementDepth();
			}

		} catch (IllegalCommunicationException e) {
			throw e;
		} catch (Throwable t) {
			Assert.fail(t);
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
//								lastHolderState.thread, "FIX ME", 
//								holder, "FIX ME"
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
		Assert.assertTrue(lockState.getDepth() > 0, "Bad prewait");
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
		Assert.assertTrue(lockState.getDepth() == 0, "Bad postwait");
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
					error(new IllegalSynchronizationException(lastHolderState, currentState));
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
			Debug.debugf("fudge", "Fudging. package name is %s", RuntimeMonitor.class.getPackage().getName());
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
	
	// Very hacky full function graph collection.
	private static String getTopFrame(StackTraceElement[] trace) {
	    if (trace == null) {
	        return "?";
	    }
	    for (StackTraceElement frame : trace) {
	        if (!frame.getClassName().equals("java.lang.Thread") && !frame.getClassName().contains("oshajava.runtime.RuntimeMonitor")) {
	            return frame.getClassName() + "." + frame.getMethodName();
	        }
	    }
	    return "?";
	}
	private static void createEdge(StackTraceElement[] writeTrace) {
	    try {
	    StackTraceElement[] readTrace = Thread.currentThread().getStackTrace();
	    String edge = getTopFrame(writeTrace) + " -> " + getTopFrame(readTrace);
	    if (createdGraphSet.add(edge)) {
	        Util.log("EDGE: " + edge);
	    }
        } catch (Throwable t) {
            Assert.fail(t);
        }
	}
	
	private static void error(IllegalCommunicationException ice) {
		switch (Config.errorActionOption.get()) {
		case HALT:
			Assert.fail(ice);
			break;
		case THROW:
			throw ice;
		case WARN:
			Assert.warn(ice.toString());
			break;
		case NONE:
			break;
		}
	}

	/**
	 * Hook to call on program exit.
	 * 
	 * @param mainClass
	 */
	public static void fini(final String mainClass) {
		Config.premainFiniTimer.stop();
		// mem and gc
		MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
		CompilationMXBean cbean = ManagementFactory.getCompilationMXBean();

		long peakMem = 0;
		for (MemoryPoolMXBean b : ManagementFactory.getMemoryPoolMXBeans()) {
			peakMem += b.getPeakUsage().getUsed();
		}
		final String threadName = Thread.currentThread().getName();
		Thread.currentThread().setName("oshajava");
		
		Util.logf("%s ms, Peak Memory: %d bytes", Config.premainFiniTimer, peakMem);
		

		// Report some stats.
		if (PROFILE) {
			Util.log("---- Profile info ------------------------------------");
		}    
		if (Stack.RECORD) {
			Stack.dumpRecordedGraphs(mainClass);
		}

		if (Config.profileOption.get() != Config.ProfileLevel.NONE) {
			// dump profile
			try {
				final PyWriter py = new PyWriter(mainClass + Config.profileExtOption.get(), !Util.quietOption.get() && Config.summaryOption.get());
				try {
					py.startMap();
					py.writeMapKey("options");
					py.startMap();
					for (Option<?> o : Option.all()) {
						py.writeMapPair(o.getId(), Py.quote(o.get()));
					}
					py.endMap();
					py.writeMapPair("threads", ThreadState.lastID() + 1);
					py.writeMapPair("frequently communicating stacks", Stack.lastID() + 1);
					for (final AbstractCounter<?> c : AbstractCounter.all()) {
						py.writeCounterAsMapPair(c);
					}
					py.writeMapPair("Memory peak", peakMem);
					py.writeMapPair("Memory used", bean.getHeapMemoryUsage().getUsed());
					py.writeMapPair("Memory max", bean.getHeapMemoryUsage().getMax());
					if (cbean!=null) {
						py.writeMapPair("Compile time", cbean.getTotalCompilationTime());
					}
					long gcTime = 0;
					for (GarbageCollectorMXBean gcb : ManagementFactory.getGarbageCollectorMXBeans()) {
						gcTime += gcb.getCollectionTime();
					}
					py.writeMapPair("GC time", gcTime);
					// times
					py.endMap();
					// END PY
				} finally {
					py.close();
				}
			} catch (IOException e) {
				Assert.warn("Failed to dump py.");
			}
		}
		
		if (PROFILE) {
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
		Thread.currentThread().setName(threadName);
	}


}
