package oshajava.sourceinfo;

import java.io.Serializable;
import java.util.HashMap;

import oshajava.runtime.RuntimeMonitor;
import oshajava.support.acme.util.Util;
import oshajava.util.count.MaxRecorder;
import oshajava.util.intset.BitVectorIntSet;

/**
 * Module specification format for saving to disk and using at runtime.
 * @author bpw
 *
 */
public class ModuleSpec implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public static final String DEFAULT_NAME = "Default";
	
	/**
	 * File extension for serialized ModuleSpec storage.
	 */
	protected static final String EXT = ".omg"; // for Osha Module Graph

	public static final MaxRecorder maxMethods = new MaxRecorder("Max methods per module");
	public static final boolean COUNT_METHODS = RuntimeMonitor.PROFILE && true;
	
	/**
	 * Kinds of communication.
	 * @author bpw
	 *
	 */
	public static enum CommunicationKind { INLINE, NONE, READ_ONLY, WRITE_ONLY, BOTH, UNCHECKED } 

	/**
	 * The module id of this module. Only instantiated at runtime.
	 */
	private transient int id = -1;
	
	/**
	 * The name of this module.
	 */
	protected final String name;
	
	/**
	 * Map from method signature to id.
	 */
	protected final HashMap<String,Integer> methodSigToId;
	
	/**
	 * Inlined methods.
	 */
	protected final BitVectorIntSet inlinedMethods;
	// TODO optimization: private final BitVectorIntSet nonCommMethods;
	
	/**
	 * Map from method id to signature.
	 */
	protected final String[] methodIdToSig;
	
	protected final int commMethods;
	
	/**
	 * The communication allowed amongst methods within the module.
	 */
	protected final Graph internalGraph;
	
	/**
	 * The module's communication interface.
	 * Graph representing the edges for which communication escapes this module.
	 */
	protected final Graph interfaceGraph;
	
	/**
	 * Create a new ModuleSpec.
	 * @param name module name
	 * @param methodIdToSig map from method id (within the module) to method signature
	 * @param internalGraph graph of internally allowed communication
	 * @param interfaceGraph graph of the interface -- where communication between module methods is visible
	 * @param inlinedMethods set of methods that are inlined
	 * @param methodSigToId map from method signature to method id
	 */
	public ModuleSpec(final String name, final String[] methodIdToSig, final int commMethods,
			final Graph internalGraph, final Graph interfaceGraph, final BitVectorIntSet inlinedMethods,
			final HashMap<String,Integer> methodSigToId) {
//		checkIntegrity();
		this.name = name;
		this.methodIdToSig = methodIdToSig;
		this.commMethods = commMethods;
		this.internalGraph = internalGraph;
		this.interfaceGraph = interfaceGraph;
		this.inlinedMethods = inlinedMethods;
		this.methodSigToId = methodSigToId;
		
	}
	
	/**
	 * Set the module ID. Module IDs are generated dynamically at runtime. Lookup and linking
	 * is done on a name basis.
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
		if (COUNT_METHODS) maxMethods.add(methodIdToSig.length);
	}
	
	/**
	 * Get the module ID.
	 * @return
	 */
	public int getId() {
		return id;
	}
	
	/**
	 * Get the module name.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get the method signature of the method with id mid. Use for error reporting.
	 * @param methodUID
	 * @return
	 */
	public String getMethodSignature(final int methodUID) {
		Util.assertTrue(Spec.getModuleID(methodUID) == id, 
				"method id " + methodUID + " (module=" + Spec.getModuleID(methodUID) 
				+ ", method=" + Spec.getMethodID(methodUID) + 
				") is not a member of module " + name + " (id " + id + ")");
		return methodIdToSig[Spec.getMethodID(methodUID)];
	}
	
	/**
	 * Get the method ID for the method with signature sig. Use for ???
	 * @param sig
	 * @return
	 */
	public int getMethodUID(final String sig) {
		Util.assertTrue(methodSigToId.containsKey(sig), "in module " + name + ", " + sig + " not found in " + methodSigToId);
		final int mid = methodSigToId.get(sig);
		return Spec.makeUID(id, mid);
	}
	
	/**
	 * Check if communication is allowed from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public boolean isAllowed(final int w, final int r) {
		Util.assertTrue(Spec.getModuleID(w) == id && Spec.getModuleID(r) == id);
		return internalGraph.containsEdge(Spec.getMethodID(w), Spec.getMethodID(r));
	}
	public boolean allAllowed(final int w, final BitVectorIntSet readers) {
		Util.assertTrue(Spec.getModuleID(w) == id); //FIXME && Spec.getModuleID(r) == id);
		final BitVectorIntSet edges = internalGraph.getOutEdges(Spec.getMethodID(w));
		if (edges == null) return false;
		return edges.containsAll(readers);
	}
	
	/**
	 * Check if communication is visible from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public boolean isPublic(final int w, final int r) {
		Util.assertTrue(Spec.getModuleID(w) == id && Spec.getModuleID(r) == id);
		return interfaceGraph.containsEdge(Spec.getMethodID(w), Spec.getMethodID(r));
	}
	
	/**
	 * Get the kind of communication in which method with id mid is involved. Options are
	 * INLINE, NONE, READ_ONLY, WRITE_ONLY, BOTH, and maybe UNCHECKED.
	 * @param mid
	 * @return
	 */
	public CommunicationKind getCommunicationKind(final int uid) {
		Util.assertTrue(Spec.getModuleID(uid) == id, "method id " + uid + " (module=" + Spec.getModuleID(uid) + ", method=" + Spec.getMethodID(uid) + ") is not a member of module " + name + " (id " + id + ")");
		final int mid = Spec.getMethodID(uid);
		if (mid >= commMethods) {
			if (inlinedMethods.contains(mid)) {
				return CommunicationKind.INLINE;
			} else {
				return CommunicationKind.NONE;
			}
		} else if (internalGraph.getOutEdges(mid).isEmpty()) {
			return CommunicationKind.READ_ONLY;
		} else if (internalGraph.hasInEdges(mid)){
			return CommunicationKind.WRITE_ONLY;
		} else {
			return CommunicationKind.BOTH;
		}
		/* else if (uncheckedMethods.contains(mid)) {
		 * return CommunicationKind.UNCHECKED;
		 */
	}
	
	private void printGraph(Graph g) {
	    for (int i = 0; i < methodSigToId.size() - inlinedMethods.size(); ++i) {
            BitVectorIntSet dests = g.getOutEdges(i);
            if (!dests.isEmpty()) {
                System.out.print("    " + methodIdToSig[i] + " -> ");
                for (int j=0; j<methodSigToId.size(); ++j) {
                    if (dests.contains(j)) {
                        System.out.print(methodIdToSig[j] + " ");
                    }
                }
                System.out.println("");
            }
        }
	}
	
	/**
	 * Prints a description of the module's policy.
	 */
	public void describe() {
        System.out.println("Name: " + name);
        System.out.println("Inlined methods:");
        for (int i=0; i<methodIdToSig.length; ++i) {
            if (inlinedMethods.contains(i)) {
                System.out.println("    " + methodIdToSig[i]);
            }
        }
        System.out.println("Internal graph:");
        printGraph(internalGraph);
        System.out.println("Interface graph:");
        printGraph(interfaceGraph);
	}

	public boolean checkIntegrity() {
		return name != null && methodIdToSig != null && internalGraph != null 
				&& interfaceGraph != null && inlinedMethods != null && methodSigToId != null;
	}
}
