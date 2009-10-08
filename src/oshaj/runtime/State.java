package oshaj.runtime;

import oshaj.Method;
import acme.util.identityhash.IdentityHashSet;

/**
 * 
 * @author bpw
 *
 */
public class State {
	
	/**
	 * Thread id of the last thread to write to the field associated with this state.
	 */
	private long writerTid;
	
	private Method writerMethod;
	
	/**
	 * Method allowed to read in the current state.
	 */
	private IdentityHashSet<Method> expectedReaders;
	
	public State(long writerTid, IdentityHashSet<Method> expectedReaders) {
		this.writerTid = writerTid;
		this.expectedReaders = expectedReaders;
	}
	
	public State(long ownerTid) {
		this(ownerTid, null);
	}
	
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
	 * @throws UnexpectedCommunicationException
	 */
	public void privateRead(long readerTid, Method readerMethod) throws UnexpectedCommunicationException {
		if (readerTid != writerTid) 
			throw new UnexpectedCommunicationException(writerTid, writerMethod, readerTid, readerMethod);
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
	 * @throws UnexpectedCommunicationException
	 */
	public synchronized void sharedRead(long readerTid, Method readerMethod) throws UnexpectedCommunicationException {
		if (readerTid != writerTid && !expectedReaders.contains(readerMethod)) 
			throw new UnexpectedCommunicationException(writerTid, writerMethod, readerTid, readerMethod);
	}
	
	/**
	 * Updates the state to reflect a write by a method with no out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param writerTid
	 * @throws UnexpectedCommunicationException
	 */
	public synchronized void privateWrite(long writerTid, Method writerMethod) throws UnexpectedCommunicationException {
		this.writerTid = writerTid;
		if (this.writerMethod != writerMethod) { 
			this.writerMethod = writerMethod;
			this.expectedReaders = null;
		}
	}
	
	/**
	 * Updates the state to reflect a write by a method with >0 out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param writerTid
	 * @param expectedReaders
	 * @throws UnexpectedCommunicationException
	 */
	public synchronized void sharedWrite(long writerTid, Method writerMethod) throws UnexpectedCommunicationException {
		this.writerTid = writerTid;
		if (this.writerMethod != writerMethod) {
			this.writerMethod = writerMethod;
			this.expectedReaders = writerMethod.getExpectedReaders();
		}
	}
}
