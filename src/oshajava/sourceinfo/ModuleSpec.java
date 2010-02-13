package oshajava.sourceinfo;

import java.io.Serializable;
import java.util.HashMap;

import oshajava.util.count.MaxRecorder;
import oshajava.util.intset.BitVectorIntSet;

/**
 * Module specification format for saving to disk and using at runtime.
 * @author bpw
 *
 */
public class ModuleSpec implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public static final MaxRecorder maxMethods = new MaxRecorder();
	public static final boolean COUNT_METHODS = true;

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
	private final String name;
	
	/**
	 * Map from method signature to id.
	 */
	private final HashMap<String,Integer> methodSigToId;
	
	/**
	 * Inlined methods.
	 */
	private final BitVectorIntSet inlinedMethods;
	// TODO optimization: private final BitVectorIntSet nonCommMethods;
	
	/**
	 * Map from method id to signature.
	 */
	private final String[] methodIdToSig;
	
	/**
	 * The communication allowed amongst methods within the module.
	 */
	private final Graph internalGraph;
	
	/**
	 * The module's communication interface.
	 * Graph representing the edges for which communication escapes this module.
	 */
	private final Graph interfaceGraph;
	
	/**
	 * Create a new ModuleSpec.
	 * @param name module name
	 * @param methodIdToSig map from method id (within the module) to method signature
	 * @param internalGraph graph of internally allowed communication
	 * @param interfaceGraph graph of the interface -- where communication between module methods is visible
	 * @param inlinedMethods set of methods that are inlined
	 * @param methodSigToId map from method signature to method id
	 */
	public ModuleSpec(final String name, final String[] methodIdToSig, final Graph internalGraph, 
			final Graph interfaceGraph, final BitVectorIntSet inlinedMethods,
			final HashMap<String,Integer> methodSigToId) {
		this.name = name;
		this.methodIdToSig = methodIdToSig;
		this.internalGraph = internalGraph;
		this.interfaceGraph = interfaceGraph;
		this.inlinedMethods = inlinedMethods;
		this.methodSigToId = methodSigToId;
		
		if (COUNT_METHODS) maxMethods.add(methodIdToSig.length);
	}
	
	/**
	 * Set the module ID. Module IDs are generated dynamically at runtime. Lookup and linking
	 * is done on a name basis.
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
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
	 * @param mid
	 * @return
	 */
	public String getMethodSignature(final int mid) {
		return methodIdToSig[mid];
	}
	
	/**
	 * Get the method ID for the method with signature sig. Use for ???
	 * @param sig
	 * @return
	 */
	public int getMethodId(final String sig) {
		final int mid = methodSigToId.get(sig);
		assert mid >= 0;
		return mid;
	}
	
	/**
	 * Check if communication is allowed from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public boolean isAllowed(final int w, final int r) {
		return internalGraph.containsEdge(w, r);
	}
	
	/**
	 * Check if communication is visible from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public boolean isPublic(final int w, final int r) {
		return interfaceGraph.containsEdge(w, r);
	}
	
	/**
	 * Get the kind of communication in which method with id mid is involved. Options are
	 * INLINE, NONE, READ_ONLY, WRITE_ONLY, BOTH, and maybe UNCHECKED.
	 * @param mid
	 * @return
	 */
	public CommunicationKind getCommunicationKind(final int mid) {
		if (inlinedMethods.contains(mid)) {
			return CommunicationKind.INLINE;
		} else if (internalGraph.getOutEdges(mid).isEmpty()) {
			if (internalGraph.hasInEdges(mid)) {
				return CommunicationKind.NONE;
			} else {
				return CommunicationKind.READ_ONLY;
			}
		} else if (internalGraph.hasInEdges(mid)){
			return CommunicationKind.WRITE_ONLY;
		} else {
			return CommunicationKind.BOTH;
		}
		/* else if (uncheckedMethods.contains(mid)) {
		 * return CommunicationKind.UNCHECKED;
		 */
	}
}
