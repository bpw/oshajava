package oshaj.runtime;

import oshaj.Spec;
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
	public long writerTid = -1;
	
	public int writerMethod = -1;
	
	/**
	 * Method allowed to read in the current state.
	 */
	private IdentityHashSet<Long> expectedReaders;
	
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
	public final void privateRead(long readerTid, int readerMethod) {
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
	 */
	public final synchronized void sharedRead(long readerTid, int readerMethod) {
		if (readerTid != writerTid && (expectedReaders == null || !expectedReaders.contains(readerMethod))) 
			throw new UnexpectedCommunicationException(writerTid, writerMethod, readerTid, readerMethod);
	}
	
	/**
	 * Updates the state to reflect a write by a method with no out-edges.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedRead or sharedWrite.
	 *  
	 * @param writerTid
	 */
	public final synchronized void privateWrite(long writerTid, int writerMethod) {
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
	 */
	public final synchronized void sharedWrite(long writerTid, int writerMethod) {
		this.writerTid = writerTid;
		if (this.writerMethod != writerMethod) {
			this.writerMethod = writerMethod;
			this.expectedReaders = Spec.expectedReadersByMethodID[writerMethod];
		}
	}
}
