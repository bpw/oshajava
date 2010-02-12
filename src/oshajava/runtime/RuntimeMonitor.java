package oshajava.runtime;

import java.lang.reflect.Array;

import oshajava.sourceinfo.BitVectorIntSet;
import oshajava.sourceinfo.Graph;
import oshajava.sourceinfo.IntSet;
import oshajava.sourceinfo.MethodTable;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;

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
	protected static final Graph graph = new Graph(MethodTable.INITIAL_METHOD_LIST_SIZE);
	private static MethodTable policy;

	public static final boolean RECORD = false;

	private static final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>(); //{
	//		@Override protected ThreadState initialValue() { return newThread(); }
	//	};

	protected static final int MAX_THREADS = 16;

	// TODO must be resize if too many threads.
	// TODO GC when a thread exits.
	private static int lastMethodTableSize = 0;
	private static final ThreadState[] threadTable = new ThreadState[MAX_THREADS];
	private static int maxThreadId = 0;

	public static void useTable(MethodTable mt) {
		policy = mt;
	}

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

	// called lazily - from threadState's lazy initializer, so whenever this thread's
	// first action is.
	private static synchronized ThreadState newThread() {
		final ThreadState ts = new ThreadState(Thread.currentThread(), policy.capacity());
		Util.assertTrue(ts.id < MAX_THREADS, "MAX_THREADS exceeded.");
		maxThreadId = ts.id;
		threadTable[ts.id] = ts;
		return ts;
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
	 * @param state
	 * @param readerMethod
	 */
	public static void read(final State write, final ThreadState reader) {
		if (write.thread != reader) {
			if (RECORD) {
				recordEdge(write.stack.method, reader.stack.method);
			}
			if (check(write.stack, reader.stack)) {
				throw new IllegalSharingException(write.thread, "writer method FIXME", 
						reader, "reader method FIXME");
			}

		}
	}

	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void arrayRead(final Object array, final int index, final ThreadState reader) {
		if (array == reader.cachedArray) {
			final State write;
			try {
				write = reader.cachedArrayIndexStates[index];
			} catch (NullPointerException e) {
				throw fudgeTrace(e);
			}
			if (write != null) {
				read(write, reader);
			}
		} else {
			final State[] states;
			try {
				states = arrayStates.get(array);
			} catch (NullPointerException e) {
				throw fudgeTrace(e);
			}
			if (states != null) {
				final State write = states[index];
				if (write != null) {
					read(write, reader);
				}
				reader.cachedArray = array;
				reader.cachedArrayIndexStates = states;
			}
		}
	}

	public static void arrayWrite(final Object array, final int index, final State currentState, final ThreadState writer) {
		if (array == writer.cachedArray) {
			writer.cachedArrayIndexStates[index] = currentState;
		} else {
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
			writer.cachedArray = array;
			writer.cachedArrayIndexStates = states;
		}
	}

	// TODO cached write array and cached read array? or is the linkage the key?

	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void coarseArrayRead(final Object array, final ThreadState reader) {
		final State write;
		if (array == reader.cachedArray) {
			write = reader.cachedArrayStateRef.contents;
		} else {
			final Ref<State> writeRef;
			try {
				writeRef = coarseArrayStates.get(array);
			} catch (NullPointerException e) {
				throw fudgeTrace(e);
			}
			if (writeRef == null) return;
			write = writeRef.contents;
			reader.cachedArray = array;
			reader.cachedArrayStateRef = writeRef;
		}
		if (write != null) {
			read(write, reader);
		}
	}

	// DO NOT CALL ON PUBLIC WRITES (i.e. when currentstate is null)
	// concurrent hash map doesn't handle nulls, plus it's faster to
	// just skip it in the first place anyway. We do have to check null
	// for inlined cases.
	public static void coarseArrayWrite(final Object array, final State currentState, final ThreadState threadState) {
		//  if no array state caching, push the null check back to bytecode.
		if (threadState.cachedArray == array) {
			threadState.cachedArrayStateRef.contents = currentState;
		} else {
			Ref<State> ref = coarseArrayStates.get(array);
			if (ref == null) {
				ref = new Ref<State>();
				ref.contents = currentState;
				final Ref<State> oldRef = coarseArrayStates.putIfAbsent(array, ref);
				if (oldRef != null) {
					ref = oldRef;
					ref.contents = currentState;
				}
			}
			threadState.cachedArray = array;
			threadState.cachedArrayStateRef = ref;
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
					lockState = new LockState(holder.currentState);
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
						if (lastHolderState != null && lastHolderState.thread != holder && holderState != null) {
							if (RECORD) {
								recordEdge(lockState.lastHolder.stack.method, holder.stack.method);
							}
							if (check(lockState.lastHolder.stack, holder.stack)) {
								throw new IllegalSynchronizationException(
										lastHolderState.thread, "FIXME", 
										holder, "FIXME"
								);
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
	public static void acquire(final ObjectWithState lock, final ThreadState holder, final State holderState) {
		LockState ls = lock.__osha_lock_state;
		if (ls == null) {
			ls = new LockState(holderState);
			ls.setDepth(1);
			lock.__osha_lock_state = ls;
		} else if (ls.getDepth() < 0) {
			Util.fail("Bad lock scoping.");
		} else if (ls.getDepth() == 0) {
			// First (non-reentrant) acquire by this thread.
			// NOTE: this is atomic, because we hold lock and no other thread can call
			// the acquire or release hooks until they hold the lock.
			final State lastHolderState = ls.lastHolder;
			if (lastHolderState != holderState) {
				ls.lastHolder = holderState;
				ls.incrementDepth();
				if (RECORD) {
					recordEdge(lastHolderState.stack.method, holder.stack.method);
				}
				if (lastHolderState != null && lastHolderState.thread != holder) {
					if (check(lastHolderState.stack, holder.stack)) {
						throw new IllegalSynchronizationException(
								lastHolderState.thread, "FIXME", 
								holder, "FIXME"
						);
					}
				}

			} else {
				ls.incrementDepth();
			}
		} else {
			// if we're already reentrant, just go one deeper.
			ls.incrementDepth();
		}
	}
	
	// TODO other granularity version.
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
	public static void postwait(final Object lock, final int resumeDepth, final ThreadState ts, final State s) {
		LockState lockState = ts.getLockState(lock);
		if (lockState == null) {
			lockState = lockStates.get(lock);
			Util.assertTrue(lockState != null && lockState.getDepth() == 0, "Bad postwait");
		}
		final State lastHolderState = lockState.lastHolder;
		if (lastHolderState != s) {
			lockState.lastHolder = s;
			lockState.setDepth(resumeDepth);
			if (RECORD) {
				recordEdge(lastHolderState.stack.method, ts.stack.method);
			}
			if (lastHolderState != null && lastHolderState.thread != ts) {
				if (check(lastHolderState.stack, ts.stack)) {
					throw new IllegalSynchronizationException(
							lastHolderState.thread, "FIXME", 
							ts, "FIXME"
					);
				}
			}
		}
	}


	// TODO make thread local? is sharing the checks worth it in time saved or does sync cost enough that it
	// should just be thread local?
	private static final ConcurrentIdentityHashMap<Stack,ConcurrentIdentityHashMap<Stack,Object>> memoTable = 
		new ConcurrentIdentityHashMap<Stack,ConcurrentIdentityHashMap<Stack,Object>>();
	
	private static final Object yes = new Object();

	public static boolean check(final Stack writer, final Stack reader) {
		try {
			if (memoTable.get(writer).contains(reader)) return true;
		} catch (NullPointerException e) {
			memoTable.putIfAbsent(writer, new ConcurrentIdentityHashMap<Stack,Object>());
		}
		if (Spec.isAllowed(writer, reader)) {
			memoTable.get(writer).putIfAbsent(reader, yes);
			return true;
		} else {
			return false;
		}
	}

	// -- Recording --------------------------------------------------------------------
	
	public static void recordRead(final State writer, final ThreadState reader) {
		if (writer.thread != reader) {
			synchronized(writer) {
				//writer.readers.add(reader.currentMethod); //FIXME
			}
		}
	}

	private static void recordEdge(int src, int dest) {
		((BitVectorIntSet)graph.getOutEdges(src)).syncedAdd(dest);
	}

	// TODO watch out for assumptions in bytecode instrumentation... null check? etc.?
	//	public static void recordArrayRead(final Object array, final int index, final ThreadState reader) {
	//		if (array == reader.cachedArray) {
	//			final State write;
	//			try {
	//				write = reader.cachedArrayIndexStates[index];
	//			} catch (NullPointerException e) {
	//				throw fudgeTrace(e);
	//			}
	//			if (write != null) {
	//				recordRead(write, reader);
	//			}
	//		} else {
	//			final State[] states;
	//			try {
	//				states = arrayStates.get(array);
	//			} catch (NullPointerException e) {
	//				throw fudgeTrace(e);
	//			}
	//			if (states != null) {
	//				final State write = states[index];
	//				if (write != null) {
	//					recordRead(write, reader);
	//				}
	//				reader.cachedArray = array;
	//				reader.cachedArrayIndexStates = states;
	//			} else {
	//				
	//			}
	//		}
	//	}
	// -- General ----------------------------------------------------------------------


	public static ThreadState getThreadState() {
		final ThreadState t = threadState.get();
		if (t == null) {
			throw new InlinedEntryPointException();
		} else {
			return t;
		}
	}

	public static ThreadState enter(final int mid) {
		try {
			//			Util.logf("enter %d", mid);
			final ThreadState ts = threadState.get();
			ts.enter(mid);
			return ts;
		} catch (NullPointerException e) {
			final ThreadState ts = newThread();
			threadState.set(ts);
			ts.enter(mid);
			return ts;
		} catch (Throwable t) {
			Util.fail(t);
			return null;
		}
	}

	public static void exit() {
		try {
			//			Util.log("exit");
			if (!threadState.get().exit()) threadState.set(null);
		} catch (Throwable t) {
			Util.fail(t);
		}
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

	public static void fini(String mainClass) {
		policy.dumpGraphML(mainClass + ".oshajava.policy.graphml");
		policy.dumpGraphML(mainClass + ".oshajava.trace.graphml", graph);
	}

}
