package oshaj;

import oshaj.util.BitVector;
import java.util.HashMap;

public class Spec {
	
	
	private static final int INITIAL_METHOD_LIST_SIZE = 1;
	
	// TODO populate from the spec. Size expectedReadersByMethodID to
	// fit exactly. We then use ID > length as an indicator of inlining
	// later on.
	// FIXME make a local copy of BitSet.
	public static final BitVector[] communicationTable = new BitVector[INITIAL_METHOD_LIST_SIZE];
	private static final int firstInlinedID = communicationTable.length;
	public static String[] methodNameByID = new String[INITIAL_METHOD_LIST_SIZE];
	private static int nextMethodID = firstInlinedID;

	// FIXME Need my own copy of any standard lib I use so it is not instrumented.
	// TODO populate from the spec...
	private static HashMap<String,Integer> methodIDbyName = new HashMap<String,Integer>();
	
	// Invariant: only called once per method.
	public static int getId(String name, String desc, String sig) {
		final String key = desc + name + sig;
		if (methodIDbyName.containsKey(key)) {
			return methodIDbyName.get(key);
		} else {
			synchronized(Spec.class) {
				// Expand vector-style if needed. Do not expand expectedReadersByMethodID. See comment on decl.
				if (nextMethodID > methodNameByID.length) {
					String[] names = new String[2*methodNameByID.length];
					System.arraycopy(methodNameByID, 0, names, 0, methodNameByID.length);
					methodNameByID = names;
				}
				return nextMethodID++;
			}
		}
	}
	
	public static boolean inlined(int id) {
		return id >= firstInlinedID;
	}
	
	public static boolean inEdges(int id) {
		return true; //FIXME
	}
	
	public static boolean outEdges(int id) {
		return true; //FIXME
	}
	
}
