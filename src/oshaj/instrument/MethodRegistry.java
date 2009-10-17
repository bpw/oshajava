package oshaj.instrument;

import java.util.HashMap;
import oshaj.util.BitVectorIntSet;
import oshaj.util.Cons;

public class MethodRegistry {
	
	/**
	 * Initial size for the method ID -> signature map.
	 */
	private static final int INITIAL_METHOD_LIST_SIZE = 512;
	
	/**
	 * Counter for introducing new method IDs.  Non-synchronized access illegal.
	 * 
	 * Invariant: must start strictly smaller than INITIAL_METHOD_LIST_SIZE;
	 */
	private static int nextID = 0;
	
	/**
	 * Map from method signature to method ID.  Non-synchronized access illegal.
	 * 
	 * FIXME Need my own copy of any standard lib I use so it is not instrumented.
	 */
	private static final HashMap<String,Integer> methodSigToID = new HashMap<String,Integer>();
	
	/**
	 * Map from method ID to method signature.  Non-synchronized access illegal.
	 */
	public static String[] methodIDtoSig = new String[INITIAL_METHOD_LIST_SIZE];
	
	/**
	 * ID requests for methods in classes that have not yet been loaded.  
	 * Non-synchronized access illegal.
	 * 
	 * FIXME need local copies...
	 */
	private static final HashMap<String,Cons<BitVectorIntSet>> idRequests = new HashMap<String,Cons<BitVectorIntSet>>();
	
	/**
	 * Register a new method by its signature, returning its unique ID.
	 * 
	 * @param sig Method signature
	 * @return Unique ID
	 */
	public static synchronized int register(String sig) {
		final int id = nextID;
		methodSigToID.put(sig, id);
		++nextID;
		
		// Add IDs to sets that have requested this signature.
		for (Cons<BitVectorIntSet> c = idRequests.get(sig); c != null; c = c.rest) {
			c.head.add(id);
			/*
			 *  TODO I've made the following assumption here:
			 *  If we ever call contains(id) on this set, then we're in the method
			 *  described by sig and id, so we must have finished loading this class.
			 *  I assume that there is a happens-before edge from class loading to
			 *  class use, so the accesses in add and contains should be well
			 *  ordered.
			 */
		}
		
		// resize if necessary.
		if (nextID == methodIDtoSig.length) {
			String[] tmp = new String[2*methodIDtoSig.length];
			System.arraycopy(methodIDtoSig, 0, tmp, 0, nextID);
			methodIDtoSig = tmp;
		}
		return id;
	}
	
	/**
	 * Lookup a method's signature by its unique ID.
	 * 
	 * @param id Method ID
	 * @return Method signature
	 */
	public static synchronized String lookup(int id) {
		assert id >= 0 && id < nextID;
		return methodIDtoSig[id];
	}
	
	/**
	 * Request that the ID of the method with signature sig be added to the given
	 * readerSet.  If a method with this signature has already been defined, then 
	 * its ID is added to the set immediately.  If a method with this signature has 
	 * not yet been defined, then its ID will be added to the set when it is
	 * defined later.
	 * 
	 * @param sig Method signature
	 * @param readerSet Set that wants the ID as a member.
	 */
	public static synchronized void requestID(String sig, BitVectorIntSet readerSet) {
		if (methodSigToID.containsKey(sig)) {
			readerSet.add(methodSigToID.get(sig));
		} else {
			idRequests.put(sig, new Cons<BitVectorIntSet>(readerSet, idRequests.get(sig)));
		} 
	}
	
	public static BitVectorIntSet buildSet(String[] readers) {
		final BitVectorIntSet set = new BitVectorIntSet();
		for (String r : readers) {
			requestID(r, set);
		}
		return set;
	}
	
}
