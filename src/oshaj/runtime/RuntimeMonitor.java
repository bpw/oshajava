package oshaj.runtime;

import oshaj.instrument.MethodRegistry;
import oshaj.util.BitVectorIntSet;
import oshaj.util.UniversalIntSet;
import oshaj.util.WeakConcurrentIdentityHashMap;
import acme.util.collections.IntStack;

public class RuntimeMonitor {

	protected static final ThreadLocal<IntStack> stacks = new ThreadLocal<IntStack>() {
		@Override protected IntStack initialValue() { return new IntStack(); }
	};

	// TODO make sure this WeakConcurrentIdentityHashMap actually works.
	// we need a concurrne hash map b/c access for multiple locks at once is not
	// protected by the app locks...
	protected static final WeakConcurrentIdentityHashMap<Object,LockState> lockStates = 
		new WeakConcurrentIdentityHashMap<Object,LockState>();

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
	 * @param readerTid
	 */
	public static void privateRead(final int readerMethod, final State state) {
		final long readerTid = Thread.currentThread().getId();
		if (readerTid != state.writerTid) 
			throw new IllegalCommunicationException(state.writerTid, state.writerMethod, readerTid, readerMethod);
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
		final long readerTid = Thread.currentThread().getId();
		if (readerTid != state.writerTid && readerMethod != state.writerMethod) 
			throw new IllegalCommunicationException(state.writerTid, state.writerMethod, readerTid, readerMethod);
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
	 * @param readerTid
	 * @param readerMethod
	 */
	public static void sharedRead(final int readerMethod, final State state) {
		final long readerTid = Thread.currentThread().getId();
		synchronized(state) {
			if (readerTid != state.writerTid && (state.readerSet == null || !state.readerSet.contains(readerMethod))) 
				throw new IllegalCommunicationException(state.writerTid, state.writerMethod, readerTid, readerMethod);
		}
	}

	/**
	 * Updates the state to reflect a write by a method with no out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param writerTid
	 */
	public static void privateWrite(final int writerMethod, final State state) {
		final long writerTid = Thread.currentThread().getId();
		synchronized(state) {
			state.writerTid = writerTid;
			if (state.writerMethod != writerMethod) { 
				state.writerMethod = writerMethod;
				state.readerSet = null;
			}
		}
	}

	public static State privateFirstWrite(final int writerMethod) {
		return new State(Thread.currentThread().getId(), writerMethod);
	}

	/**
	 * Updates the state to reflect a write by a method with >0 out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param writerTid
	 * @param readerSet
	 */
	public static void protectedWrite(final int writerMethod, final State state) {
		final long writerTid = Thread.currentThread().getId();
		synchronized(state) {
			state.writerTid = writerTid;
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = MethodRegistry.policyTable[writerMethod];
			}
		}
	}

	public static State protectedFirstWrite(final int writerMethod) {
		return new State(Thread.currentThread().getId(), writerMethod, MethodRegistry.policyTable[writerMethod]);
	}

	public static void publicWrite(final int writerMethod, final State state) {
		final long writerTid = Thread.currentThread().getId();
		synchronized(state) {
			state.writerTid = writerTid;
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = UniversalIntSet.set;
			}
		}
	}

	public static State publicFirstWrite(final int writerMethod) {
		return new State(Thread.currentThread().getId(), writerMethod, UniversalIntSet.set);
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
		lockStates.get(lock).depth--;
	}

	/**
	 * Lock acquire hook.
	 * 
	 * @param readerSet
	 * @param mid
	 * @param lock
	 */
	public static void acquire(final int mid, final Object lock) {
		final long tid = Thread.currentThread().getId();
		LockState state = lockStates.get(lock);
		if (state == null) {
			state = lockStates.put(lock, new LockState(tid, mid, MethodRegistry.policyTable[mid]));
			assert state == null;
		} else if (state.depth == 0) {
			// only care on first (non-reentrant) acquire, since it matches with what will be
			// the last (real) release.
			if (tid != state.writerTid && (state.readerSet == null || ! state.readerSet.contains(mid))) {
				throw new IllegalSynchronizationException(state.writerTid, state.writerMethod, tid, mid);
			} else {
				state.writerMethod = mid;
				state.writerTid = tid;
				state.readerSet = MethodRegistry.policyTable[mid];
			}
		} else if (state.depth < 0) {
			throw new IllegalStateException("Bad lock scoping.");
		}
	}
	
	public static void enter(final int mid) {
		stacks.get().push(mid);
	}

	public static void exit() {
		stacks.get().pop();
	}
	
	public static int currentMid() { // TODO could replace with opt. to have privateRead, inlinePrivateRead, etc.
		return stacks.get().top();
	}

	public static BitVectorIntSet buildSet(String[] readers) {
		final BitVectorIntSet set = new BitVectorIntSet();
		for (String r : readers) {
			MethodRegistry.requestID(r, set);
		}
		return set;
	}

}
