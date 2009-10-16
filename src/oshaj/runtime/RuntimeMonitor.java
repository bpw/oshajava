package oshaj.runtime;

import oshaj.Spec;
import acme.util.collections.IntStack;

public class RuntimeMonitor {
	
	protected static final ThreadLocal<IntStack> stacks = new ThreadLocal<IntStack>() {
		@Override protected IntStack initialValue() { return new IntStack(); }
	};
	
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
	public static void privateRead(State state, long readerTid, int readerMethod) {
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
	public static void sharedRead(State state, long readerTid, int readerMethod) {
		synchronized(state) {
			if (readerTid != state.writerTid && (state.readerList == null || !state.readerList.get(readerMethod))) 
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
	public static void privateWrite(State state, long writerTid, int writerMethod) {
		synchronized(state) {
			state.writerTid = writerTid;
			if (state.writerMethod != writerMethod) { 
				state.writerMethod = writerMethod;
				state.readerList = null;
			}
		}
	}
	
	/**
	 * Updates the state to reflect a write by a method with >0 out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param writerTid
	 * @param readerList
	 */
	public static void sharedWrite(State state, long writerTid, int writerMethod) {
		synchronized(state) {
			state.writerTid = writerTid;
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerList = Spec.communicationTable[writerMethod];
			}
		}
	}

	public static void arrayRead() {}

	public static void arrayWrite() {}
	
	public static void acquire() {}
	
	public static void release() {}
	
	public static void enter(int mid) {
		stacks.get().push(mid);
	}
	
	public static void exit(int mid) {
		int emid = stacks.get().pop();
		assert emid == mid;
	}

}
