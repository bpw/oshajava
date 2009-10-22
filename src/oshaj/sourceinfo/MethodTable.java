package oshaj.sourceinfo;

import java.util.HashMap;

import acme.util.Util;

public class MethodTable {
	
	/**
	 * Initial size for the method ID -> signature map.
	 */
	private static final int INITIAL_METHOD_LIST_SIZE = 1024;
	
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
	private static String[] methodIDtoSig = new String[INITIAL_METHOD_LIST_SIZE];
	
	public static IntSet[] policyTable = new IntSet[INITIAL_METHOD_LIST_SIZE];
	
	/**
	 * ID requests for methods in classes that have not yet been loaded.  
	 * Non-synchronized access illegal.
	 * 
	 * FIXME need local copies...
	 */
	private static final HashMap<String,Cons<BitVectorIntSet>> idRequests = new HashMap<String,Cons<BitVectorIntSet>>();
		
	
	public static synchronized int size() {
		return nextID;
	}
	public static synchronized int capacity() {
		return policyTable.length;
	}
	/**
	 * Register a new method by its signature, returning its unique ID.
	 * 
	 * @param sig Method signature
	 * @return Unique ID
	 */
	public static synchronized int register(final String sig, final IntSet readerSet) {
		final int id = nextID;
		methodSigToID.put(sig, id);
		policyTable[id] = readerSet;
		methodIDtoSig[id] = sig;
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
		idRequests.remove(sig);
		
		// resize if necessary.
		if (nextID == policyTable.length) {
			upsize();
		}
		return id;
	}
	
	private static synchronized void upsize() {
		Util.yikes("!!!!! UNSAFE resize triggered. !!!!!");
		String[] tmp = new String[2*nextID];
		System.arraycopy(methodIDtoSig, 0, tmp, 0, nextID);
		methodIDtoSig = tmp;
		IntSet[] p = new IntSet[2*nextID];
		System.arraycopy(policyTable, 0, p, 0, nextID);
		policyTable = p;
		// upsize the state table.
		// TODO RuntimeMonitor.upsizeStateTable();
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
	
	public static synchronized IntSet getPolicy(String name) {
		final Integer id = methodSigToID.get(name);
		if (id == null) Util.fail("Not in there.");
		return MethodTable.policyTable[id];
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
	
}
