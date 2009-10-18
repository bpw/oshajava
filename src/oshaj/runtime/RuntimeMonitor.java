package oshaj.runtime;

import oshaj.instrument.MethodRegistry;
import oshaj.util.BitVectorIntSet;
import oshaj.util.UniversalIntSet;
import oshaj.util.WeakConcurrentIdentityHashMap;
import acme.util.Util;
import acme.util.collections.IntStack;
import acme.util.identityhash.ConcurrentIdentityHashMap;

public class RuntimeMonitor {

	protected static final ThreadLocal<IntStack> stacks = new ThreadLocal<IntStack>() {
		@Override protected IntStack initialValue() { return new IntStack(); }
	};

	// TODO fix WeakConcurrentIdentityHashMap and replace with that..
	// we need a concurrent hash map b/c access for multiple locks at once is not
	// protected by the app locks...
	protected static final ConcurrentIdentityHashMap<Object,LockState> lockStates = 
		new ConcurrentIdentityHashMap<Object,LockState>();

	/**
	 * Checks a read by a private reading method, i.e. a method that has no
	 * in-edges and can only read data written by the same thread.
	 * 
	 * TODO dynamic spec-loading prevents the use of this fast path for the 
	 * time being.  Pre-processing of the whole spec is needed to know that
	 * a method has no in-edges.
	 * 
	 * TODO note that you can't have one in the same program as a public writer.
	 * 
	 * This method need not be synchronized. Only one read is performed. If it
	 * happens to interleave with a privateRead or sharedRead call, no harm done
	 * since there's no writing done.  If it happens to interleave with a 
	 * privateWrite call or a sharedWrite call, we get luck of the draw since the
	 * application did not order these calls. Fine. We're not a race detector.
	 * 
	 * @param readerMethod
	 * @param state
	 */
	public static void privateRead(final int readerMethod, final State state) {
		final Thread readerThread = Thread.currentThread();
		if (readerThread != state.writerThread) 
			throw new IllegalSharingException(
					state.writerThread, MethodRegistry.lookup(state.writerMethod), 
					readerThread, MethodRegistry.lookup(readerMethod)
			);
	}

	/**
	 * Checks a read by a self-reading method, i.e. a method that has only one
	 * in edge, a self edge. (Another future optimization.)
	 * 
	 * TODO also, a SingletonIntSet to optimize reads in methods with one
	 * (non-self) in edge.
	 * 
	 * @param readerMethod
	 * @param state
	 */
	public static void selfRead(final int readerMethod, final State state) {
		final Thread readerThread = Thread.currentThread();
		if (readerThread != state.writerThread && readerMethod != state.writerMethod) 
			throw new IllegalSharingException(
					state.writerThread, MethodRegistry.lookup(state.writerMethod), 
					readerThread, MethodRegistry.lookup(readerMethod)
			);
	}

	/**
	 * Checks a read by a shared reading method, i.e. a method that has in-edges
	 * and can read a write by the same thread or a write in one of the
	 * expectedReaders methods.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedWrite or privateWrite. A cleverer solution using volatiles could 
	 * work too.
	 * 
	 * @param state
	 * @param readerMethod
	 */
	public static void sharedRead(final State state, final int readerMethod) {
		final Thread readerThread = Thread.currentThread();
		synchronized(state) {
			if (readerThread != state.writerThread && (state.readerSet == null || !state.readerSet.contains(readerMethod))) 
				throw new IllegalSharingException(
						state.writerThread, MethodRegistry.lookup(state.writerMethod), 
						readerThread, MethodRegistry.lookup(readerMethod)
				);
		}
	}

	/**
	 * Updates the state to reflect a write by a method with no out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param state
	 * @param writerMethod
	 */
	public static void privateWrite(final State state, final int writerMethod) {
		final Thread writerThread = Thread.currentThread();
		synchronized(state) {
			state.writerThread = writerThread;
			if (state.writerMethod != writerMethod) { 
				state.writerMethod = writerMethod;
				state.readerSet = null;
			}
		}
	}

	public static State privateFirstWrite(final int writerMethod) {
		return new State(Thread.currentThread(), writerMethod);
	}

	/**
	 * Updates the state to reflect a write by a method with >0 out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param state
	 * @param writerMethod
	 */
	public static void protectedWrite(final State state, final int writerMethod) {
		final Thread writerThread = Thread.currentThread();
		synchronized(state) {
			state.writerThread = writerThread;
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = MethodRegistry.policyTable[writerMethod];
			}
		}
	}

	public static State protectedFirstWrite(final int writerMethod) {
		return new State(Thread.currentThread(), writerMethod, MethodRegistry.policyTable[writerMethod]);
	}

	public static void publicWrite(final State state, final int writerMethod) {
		final Thread writerThread = Thread.currentThread();
		synchronized(state) {
			state.writerThread = writerThread;
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = UniversalIntSet.set;
			}
		}
	}

	public static State publicFirstWrite(final int writerMethod) {
		return new State(Thread.currentThread(), writerMethod, UniversalIntSet.set);
	}


	public static void arrayRead() {}

	public static void arrayWrite() {}

	/**
	 * Lock release hook.  Since things are well-scoped in Java, we'll just do all the work
	 * in acquire.  Only need to hit the reentrancy counter here.
	 * 
	 * @param lock
	 */
	public static void release(final Object lock) {
		final LockState state = lockStates.get(lock);
		if (state.depth <= 0) Util.fail("Bad lock scoping");
		state.depth--;
//		if (state.depth == 0) Util.logf("real release %s", Util.objectToIdentityString(lock));
	}

	/**
	 * Lock acquire hook.
	 * 
	 * TODO cache locks by thread.
	 * 
	 * @param readerSet
	 * @param mid
	 * @param lock
	 */
	public static void acquire(final Object lock, final int mid) {
		try {
			LockState state = lockStates.get(lock);
			if (state == null) {
				state = new LockState(Thread.currentThread(), mid, MethodRegistry.policyTable[mid]);
				state.depth = 1;
				state = lockStates.put(lock, state);
				assert state == null;
			} else if (state.depth < 0) {
				Util.fail("Bad lock scoping.");
			} else if (state.depth == 0) {
				// only care on first (non-reentrant) acquire, since it matches with what will be
				// the last (real) release.
				final Thread acquirerThread = Thread.currentThread();
				if (acquirerThread != state.writerThread && (state.readerSet == null || ! state.readerSet.contains(mid))) {
					throw new IllegalSynchronizationException(
							state.writerThread, MethodRegistry.lookup(state.writerMethod), 
							acquirerThread, MethodRegistry.lookup(mid));
				} else {
					state.writerMethod = mid;
					state.writerThread = acquirerThread;
					state.readerSet = MethodRegistry.policyTable[mid];
					state.depth++;
				}
			} else {
				state.depth++;
			}
		} catch (IllegalCommunicationException e) {
			throw e;
		} catch (Throwable t) {
			Util.fail(t);
		}
	}
	
	public static void enter(final int mid) {
		try {
			Util.logf("enter %d", mid);
			stacks.get().push(mid);
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	public static void exit() {
		try {
			Util.log("exit");
			stacks.get().pop();
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	public static int currentMid() { // TODO could replace with opt. to have privateRead, inlinePrivateRead, etc.
		try {
			return stacks.get().top();
		} catch (Throwable t) {
			Util.fail(t);
			return -1;
		}
	}

}
