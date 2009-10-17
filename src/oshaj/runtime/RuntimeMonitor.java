package oshaj.runtime;

import oshaj.instrument.MethodRegistry;
import oshaj.util.BitVectorIntSet;
import oshaj.util.IntSet;
import oshaj.util.UniversalIntSet;
import oshaj.util.WeakConcurrentIdentityHashMap;
import acme.util.collections.IntStack;

public class RuntimeMonitor {
	
	protected static final ThreadLocal<IntStack> stacks = new ThreadLocal<IntStack>() {
		@Override protected IntStack initialValue() { return new IntStack(); }
	};
	
	// TODO make sure this WeakConcurrentIdentityHashMap actually works.
	protected static final WeakConcurrentIdentityHashMap<Object,State> lockStates = 
		new WeakConcurrentIdentityHashMap<Object,State>();
	
	/**
	 * Checks a read by a private reading method, i.e. a method that has no
	 * in-edges and can only read data written by the same thread.
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
	public static void protectedWrite(final int writerMethod, final State state, final IntSet readerSet) {
		final long writerTid = Thread.currentThread().getId();
		synchronized(state) {
			state.writerTid = writerTid;
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = readerSet;
			}
		}
	}
	
	public static State protectedFirstWrite(final int writerMethod, final IntSet readerSet) {
		return new State(Thread.currentThread().getId(), writerMethod, readerSet);
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
	
	public static void release(final int mid, final Object lock, final IntSet readerSet) {
		final long tid = Thread.currentThread().getId();
		State state = lockStates.get(lock);
		if (state == null) {
			state = lockStates.putIfAbsent(lock, new State(tid, mid, readerSet));
		} 
		if (state != null) {
			state.writerMethod = mid;
			state.writerTid = tid;
			state.readerSet = readerSet;
		}
	}
	
	public static void acquire(final int mid, final Object lock) {
		final long tid = Thread.currentThread().getId();
		State state = lockStates.get(lock);
		if (state != null && (tid != state.writerTid || ! state.readerSet.contains(mid))) 
			throw new IllegalSynchronizationException(state.writerTid, state.writerMethod, tid, mid);
	}
	
	public static void enter(final int mid) {
		stacks.get().push(mid);
	}
	
	public static void exit(final int mid) {
		int emid = stacks.get().pop();
		assert emid == mid;
	}
	
	public static BitVectorIntSet buildSet(String[] readers) {
		final BitVectorIntSet set = new BitVectorIntSet();
		for (String r : readers) {
			MethodRegistry.requestID(r, set);
		}
		return set;
	}
	
}
