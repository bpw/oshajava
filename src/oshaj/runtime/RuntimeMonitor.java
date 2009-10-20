package oshaj.runtime;

import oshaj.sourceinfo.MethodTable;
import oshaj.util.IntSet;
import oshaj.util.UniversalIntSet;
import acme.util.Util;
import acme.util.identityhash.ConcurrentIdentityHashMap;

/**
 * TODO Possible optimizations:
 *  
 * 1. store a stack of method sets instead of method ids (i.e. do the array load 
 *    in the enter and exit hooks instead of in each read, write, and acquire hook.
 *    Hopefully this is less often, but who knows.)
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
 * 4. Use volatile instead of synchronized for state accesses (see optSharedRead and
 *    optSharedWrite, below). Probably a bad idea, b/c of race potential etc., but
 *    read up on that JMM... It also means that ICEs are not guaranteed to report
 *    *blame* correctly. (They are still validly illegal communication...)
 *    
 * 5. Store the currentThread in a local in each method. This way, Thread.currentThread()
 *    is called only once per method invocation. Then pass it as an argument to each
 *    hook that needs it.
 *    
 * 6. Inline the writerThread == currentThread check for reads. (i.e. do it outside the
 *    hook and only call the hook if needed.)
 *    
 *    
 * TODO Things to fix or add.
 * 
 * + Copy (partial) of java.*
 * 
 * + Copy of acme.* (using our copy of java.*)
 * 
 * + Copy of asm.* (using our copy of java.*)
 * 
 * + Arrays
 * 
 * + command line options
 * 
 * + Graph recording (as annotation inference? i.e. insert annotations in the bytecode?)
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

	protected static final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>() {
		@Override protected ThreadState initialValue() { return new ThreadState(Thread.currentThread()); }
	};

	// TODO fix WeakConcurrentIdentityHashMap and replace with that..
	// we need a concurrent hash map b/c access for multiple locks at once is not
	// protected by the app locks...
	protected static final ConcurrentIdentityHashMap<Object,LockState> lockStates = 
		new ConcurrentIdentityHashMap<Object,LockState>();

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
		final ThreadState readerThread = threadState.get();
		// volatile read
		final ThreadState writerThread = state.writerThread;
		if (writerThread != readerThread) {
			synchronized(state) {
				// Even though readerSet may have been written by a different thread
				// than writerThread, it is still true that they were not written
				// by readerThread.
				final IntSet readerSet = state.readerSet;				
				if (readerSet == null || ! readerSet.contains(readerMethod)) {
					// in here, speed is not a big deal.
					throw new IllegalSharingException(
							state.writerThread, MethodTable.lookup(state.writerMethod), 
							readerThread, MethodTable.lookup(readerMethod)
					);
				}
			}
		}
	}
	
	// same as sharedRead
	public static void inlineRead(final State state) {
		final ThreadState readerThread = threadState.get();
		// volatile read
		final ThreadState writerThread = state.writerThread;
		if (writerThread != readerThread) {
			synchronized(state) {
				// Even though readerSet may have been written by a different thread
				// than writerThread, it is still true that they were not written
				// by readerThread.
				final IntSet readerSet = state.readerSet;				
				if (readerSet == null || ! readerSet.contains(readerThread.currentMethod())) {
					// in here, speed is not a big deal.
					throw new IllegalSharingException(
							state.writerThread, MethodTable.lookup(state.writerMethod), 
							readerThread, MethodTable.lookup(readerThread.currentMethod())
					);
				}
			}
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
		final ThreadState writerThread = threadState.get();
		synchronized(state) {
			if (state.writerMethod != writerMethod) { 
				state.writerMethod = writerMethod;
				state.readerSet = null;
			}
			state.writerThread = writerThread; // sync edge to volatile read in privateRead()
		}
	}

	public static State privateFirstWrite(final int writerMethod) {
		return new State(threadState.get(), writerMethod);
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
		final ThreadState writerThread = threadState.get();
		synchronized(state) {
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = writerThread.currentReaderSet;
			}
			state.writerThread = writerThread;
		}
	}

	public static State protectedFirstWrite(final int writerMethod) {
		return new State(threadState.get(), writerMethod, MethodTable.policyTable[writerMethod]);
	}

	// same as protectedWrite.
	public static void inlineWrite(final State state) {
		final ThreadState writerThread = threadState.get();
		final int writerMethod = writerThread.currentMethod();
		synchronized(state) {
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = writerThread.currentReaderSet;
			}
			state.writerThread = writerThread;
		}
	}

	// same as protectedFirstWrite.
	public static State inlineFirstWrite() {
		final ThreadState writerThread = threadState.get();
		return new State(threadState.get(), writerThread.currentMethod(), writerThread.currentReaderSet);
	}

	public static void publicWrite(final State state, final int writerMethod) {
		final ThreadState writerThread = threadState.get();
		synchronized(state) {
			if (state.writerMethod != writerMethod) {
				state.writerMethod = writerMethod;
				state.readerSet = UniversalIntSet.set;
			}
			state.writerThread = writerThread;
		}
	}

	public static State publicFirstWrite(final int writerMethod) {
		return new State(threadState.get(), writerMethod, UniversalIntSet.set);
	}


	public static void arrayRead() {}

	public static void arrayWrite() {}

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
	public static void release(final Object lock) {
		try {
			final LockState state = lockStates.get(lock);
			if (state.depth <= 0) Util.fail("Bad lock scoping");
			state.depth--;
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	/**
	 * Lock acquire hook.
	 * 
	 * TODO cache locks by thread.
	 * 
	 * @param nextMethods
	 * @param holderMethod
	 * @param lock
	 */
	public static void acquire(final Object lock, final int holderMethod) {
		try {
			LockState state = lockStates.get(lock);
			if (state == null) {
				state = new LockState(threadState.get(), holderMethod, MethodTable.policyTable[holderMethod]);
				state.depth = 1;
				state = lockStates.put(lock, state);
				Util.assertTrue(state == null);
			} else if (state.depth < 0) {
				Util.fail("Bad lock scoping.");
			} else if (state.depth == 0) {
				// First (non-reentrant) acquire by this thread.
				// NOTE: this is all atomic, because we hold lock and no other thread can call
				// the acquire or release hooks until they hold the lock.
				final ThreadState holderThread = threadState.get();
				final IntSet readerSet = state.nextMethods;
				if (state.lastMethod != holderMethod) {
					state.lastMethod = holderMethod;
					state.nextMethods = MethodTable.policyTable[holderMethod];					
				}
				state.depth++;
				if (state.lastThread != holderThread) {
					state.lastThread = holderThread;
					if (readerSet == null || ! readerSet.contains(holderMethod)) {
						throw new IllegalSynchronizationException(
								state.lastThread, MethodTable.lookup(state.lastMethod), 
								holderThread, MethodTable.lookup(holderMethod)
						);
					}
				}
			} else {
				// if we're already reentrant, just go one deeper.
				state.depth++;
			}
		} catch (IllegalCommunicationException e) {
			throw e;
		} catch (Throwable t) {
			Util.fail(t);
		}
	}
	
	public static void inlineAcquire(final Object lock) {
		// TODO when code stabilizes, make this a copy of acquire, to avoid loading from
		// threadState.get() twice.
		acquire(lock, threadState.get().currentMethod());
	}
	
	public static void enter(final int mid) {
		try {
//			Util.logf("enter %d", mid);
			threadState.get().enter(mid, MethodTable.policyTable[mid]);
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	public static void exit() {
		try {
//			Util.log("exit");
			threadState.get().exit();
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	public static int currentMid() { // TODO could replace with opt. to have privateRead, inlinePrivateRead, etc.
		try {
			return threadState.get().currentMethod();
		} catch (ArrayIndexOutOfBoundsException e) {
			Util.fail(new InlinedEntryPointException());
			return -1;
		}
	}
	
	
	// -- TODO ----------------------------------------------
//	/**
//	 * Checks a read by a private reading method, i.e. a method that has no
//	 * in-edges and can only read data written by the same thread.
//	 * 
//	 * TODO dynamic spec-loading prevents the use of this fast path for the 
//	 * time being.  Pre-processing of the whole spec is needed to know that
//	 * a method has no in-edges.
//	 * 
//	 * TODO note that you can't have one in the same program as a public writer.
//	 * 
//	 * This method need not be synchronized. Only one read is performed. If it
//	 * happens to interleave with a privateRead or sharedRead call, no harm done
//	 * since there's no writing done.  If it happens to interleave with a 
//	 * privateWrite call or a sharedWrite call, we get luck of the draw since the
//	 * application did not order these calls. Fine. We're not a race detector.
//	 * 
//	 * @param readerMethod
//	 * @param state
//	 */
//	public static void privateRead(final State state, final int readerMethod) {
//		final Thread readerThread = Thread.currentThread();
//		final Thread writerThread;
//		final int writerId;
//		synchronized(state) {
//			writerThread = state.writerThread;
//			writerId = state.writerMethod;
//		}
//		if (readerThread != writerThread) 
//			throw new IllegalSharingException(
//					writerThread, MethodRegistry.lookup(writerId), 
//					readerThread, MethodRegistry.lookup(readerMethod)
//			);
//	}
//
//
//
//	public static void optSharedRead(final State state, final int readerMid) {
//	final Thread readerThread = Thread.currentThread();
//	// volatile read: if someone else has written, then we'll know.
//	final Thread writerThread = state.writerThread;
//	if (writerThread != readerThread) {
//		// a third thread != writerThread and != readerThread could write between that check
//		// and this load of readerSet.  Likewise, writerThread could write again in a different
//		// method. This is potentially problematic if we want to report the correct illegal 
//		// communication, but is fine otherwise, because if the check below fire, there was the
//		// potential for *some* illegal communication.
//		
//		// non-volatile read:
//		final AbstractMethodSet readerSet = state.readerSet;
//		if (readerSet == null || ! readerSet.contains(readerMid)) {
//			// writerThread and readerSet are potentially from different accesses, possibly even
//			// in different threads, but it is true that this communication was illegal, because.
//			// 
//			throw new IllegalSharingException(
//					writerThread, MethodRegistry.lookup(readerSet.owner), 
//					readerThread, MethodRegistry.lookup(readerMid)
//			);
//		}
//	}
//}
//
//public static void optSharedWrite(final State state, final int writerMid) {
//	final Thread writerThread = Thread.currentThread();
//	// non-volatile write
//	state.readerSet = MethodRegistry.policyTable[writerMid];
//	// volatile write
//	state.writerThread = writerThread;
//}
//
}
