package oshajava.sourceinfo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;

import oshajava.support.acme.util.Util;


public class MethodTable extends Graph {
	
	/**
	 * Initial size for the method ID -> signature map.
	 */
	public static final int INITIAL_METHOD_LIST_SIZE = 1024;
		
	/**
	 * Map from method signature to method ID.  Non-synchronized access illegal.
	 * 
	 * FIXME Need my own copy of any standard lib I use so it is not instrumented.
	 */
	private final HashMap<String,Integer> methodSigToID = new HashMap<String,Integer>();
	
	/**
	 * Map from method ID to method signature.  Non-synchronized access illegal.
	 */
	private String[] methodIDtoSig = new String[INITIAL_METHOD_LIST_SIZE];

	/**
	 * ID requests for methods in classes that have not yet been loaded.  
	 * Non-synchronized access illegal.
	 * 
	 * FIXME need local copies...
	 */
	private final HashMap<String,Cons<BitVectorIntSet>> idRequests = new HashMap<String,Cons<BitVectorIntSet>>();
	
	public MethodTable() {
		super(INITIAL_METHOD_LIST_SIZE);
	}
	
	public synchronized int size() {
		return super.size();
	}
	
	public synchronized int capacity() {
		return super.capacity();
	}
	
	/**
	 * Register a new method by its signature, returning its unique ID.
	 * 
	 * @param sig Method signature
	 * @return Unique ID
	 */
	public synchronized int register(final String sig, final IntSet readerSet) {
		if (methodSigToID.containsKey(sig)) {
			return methodSigToID.get(sig);
		} else {
			final int id = add(readerSet);
			methodSigToID.put(sig, id);
			methodIDtoSig[id] = sig;

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
			if (size() == capacity()) {
				upsize();
			}
			return id;
		}
	}
	
	private synchronized void upsize() {
		Util.yikes("!!!!! UNSAFE resize triggered. !!!!!");
		final int newSize = 2*size();
		String[] tmp = new String[newSize];
		System.arraycopy(methodIDtoSig, 0, tmp, 0, size());
		methodIDtoSig = tmp;
		resize(newSize);
	}
	
	/**
	 * Lookup a method's signature by its unique ID.
	 * 
	 * @param id Method ID
	 * @return Method signature
	 */
	public synchronized String lookup(int id) {
		assert id >= 0 && id < size();
		return methodIDtoSig[id];
	}
	
	public synchronized IntSet getPolicy(String name) {
		final Integer id = methodSigToID.get(name);
		if (id == null) Util.fail("Not in there.");
		return getOutEdges(id);
	}
	
	/**
	 * Request that the ID of the method with signature sig be added to the given
	 * readerSet.  If a method with this signature has already been defined, then 
	 * its ID is added to the set immediately.  If a method with this signature has 
	 * not yet been defined, then its ID will be added to the set when it is
	 * defined later.
	 * 
	 * NOTE RuntimeMonitor.loadNewMethods depends on this lazy implementation to
	 * guarantee "reasonable" ordering of IDs.
	 * 
	 * @param sig Method signature
	 * @param readerSet Set that wants the ID as a member.
	 */
	public synchronized void requestID(String sig, BitVectorIntSet readerSet) {
		if (methodSigToID.containsKey(sig)) {
			readerSet.add(methodSigToID.get(sig));
		} else {
			idRequests.put(sig, new Cons<BitVectorIntSet>(readerSet, idRequests.get(sig)));
		} 
	}
	
	public void dumpGraphML(String file) {
		dumpGraphML(file, this);
	}
	public void dumpGraphML(String file, Graph g) {
		try {
			final Writer graphml = new PrintWriter(new File(file));
			// print boilerplate.
			graphml.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			graphml.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
			graphml.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
			graphml.write("xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n");
			graphml.write(" http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
			graphml.write("<graph id=\"G\" edgedefault=\"directed\">\n");
			graphml.write("<!-- data schema -->\n\n");
			graphml.write("<key id=\"fnname\" for=\"node\" attr.name=\"fnname\" attr.type=\"string\"/>\n");
			graphml.write("<key id=\"file\" for=\"node\" attr.name=\"file\" attr.type=\"string\"/>\n");
			graphml.write("<key id=\"kind\" for=\"edge\" attr.name=\"kind\" attr.type=\"string\"/>\n\n\n");
			graphml.write("<key id=\"weight\" for=\"edge\" attr.name=\"weight\" attr.type=\"double\"/>\n");
			for (int i = 0; i < g.size(); i++) {
				graphml.write("<node id=\"" + i + "\">\n");
				graphml.write("\t<data key=\"fnname\">" + filterForXml(methodIDtoSig[i]) + "</data>\n");
				graphml.write("\t<data key=\"file\">" + filterForXml(methodIDtoSig[i].substring(0, methodIDtoSig[i].lastIndexOf('.'))) + "</data>\n");
				graphml.write("</node>\n");
				for (int j = 0; j < g.size(); j++) {
					IntSet set = g.getOutEdges(i);
					if (set != null && set.contains(j)) {
						graphml.write("<edge source=\"" + i + "\" target=\"" + j + "\">\n");
						graphml.write("\t<data key=\"kind\">rw</data>\n");
						graphml.write("\t<data key=\"weight\">" + 1 + "</data>\n");
						graphml.write("</edge>\n");
					}
				}
			}
			graphml.write("</graph>\n</graphml>\n");
			graphml.close();
		} catch (final IOException e) {
			Util.fail(e);
		}
	}
	private static String filterForXml(String s) {
		return s.replace('<', '{').replace('>', '}');
	}

}
