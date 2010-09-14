package oshajava.spec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oshajava.instrument.InstrumentationAgent;
import oshajava.runtime.RuntimeMonitor;
import oshajava.support.acme.util.Util;
import oshajava.util.count.MaxRecorder;
import oshajava.util.intset.BitVectorIntSet;
/**
 * Module specification format for saving to disk and using at runtime.
 * @author bpw
 *
 */
public class CompiledModuleSpec extends ModuleSpec {
	
	private static final long serialVersionUID = 2L;
	
	public static final String DEFAULT_NAME = "Default";
	
	/**
	 * File extension for serialized ModuleSpec storage.
	 */
	protected static final String EXT = ".oms"; // for Osha Module Spec

	public static final MaxRecorder maxCommMethods = new MaxRecorder("Max comm. methods per module");
	public static final MaxRecorder maxInterfaceMethods = new MaxRecorder("Max interface methods per module");
	public static final MaxRecorder maxMethods = new MaxRecorder("Max total methods per module");
	public static final boolean COUNT_METHODS = RuntimeMonitor.PROFILE && true;
	
	/**
	 * Map from method signature to id.
	 */
	protected final HashMap<String,Integer> methodSigToId;
	
	/**
	 * Map from method id to signature.
	 */
	protected final String[] methodIdToSig;
	
//	protected final int commMethods;
	protected final int firstNoncommMethod, firstInlinedMethod;
	
	/**
	 * The communication allowed amongst methods within the module.
	 */
	protected final Graph commGraph;
	
	/**
	 * The module's communication interface.
	 * Graph representing the edges for which communication escapes this module.
	 */
	protected final Graph interfaceGraph;
	
	/**
	 * Create a new ModuleSpec.
	 * @param name module name
	 * @param methodIdToSig map from method id (within the module) to method signature
	 * @param interfaceGraph graph of the interface -- where communication between module methods is visible
	 * @param methodSigToId map from method signature to method id
	 */
	public CompiledModuleSpec(final String name, final Map<String,MethodSpec> methodSpecs) {
		super(InstrumentationAgent.internalName(name));
		
		final List<String> idToSig = new ArrayList<String>();
		methodSigToId = new HashMap<String,Integer>();
		final List<String> inlinedMethods = new ArrayList<String>();
		final List<String> noncommMethods = new ArrayList<String>();
		
		for (final String sig : methodSpecs.keySet()) {
			if (methodSpecs.get(sig) == MethodSpec.INLINE) {
				inlinedMethods.add(sig);
			} else if (methodSpecs.get(sig) == MethodSpec.NONCOMM) {
				noncommMethods.add(sig);
			} else {
				methodSigToId.put(sig, idToSig.size());
				idToSig.add(sig);
			}
		}
		
		commGraph = new Graph(idToSig.size());
		interfaceGraph = new Graph(idToSig.size());

		for (int i = 0; i < idToSig.size(); i++) {
			final MethodSpec m = methodSpecs.get(idToSig.get(i));
			final BitVectorIntSet commReaders = commGraph.getOutEdges(i);
			final BitVectorIntSet ifaceReaders = interfaceGraph.getOutEdges(i);
			if (m.writeGroups() != null) {
				for (final Group g : m.writeGroups()) {
					final BitVectorIntSet readers = g.kind() == Group.Kind.COMM ? commReaders : ifaceReaders;
					for (String reader : g.readers()) {
						readers.add(methodSigToId.get(reader));
					}
				}
			}
		}
		
		firstNoncommMethod = idToSig.size();
		for (final String sig : noncommMethods) {
			methodSigToId.put(sig, idToSig.size());
			idToSig.add(sig);
		}
		firstInlinedMethod = idToSig.size();
		for (final String sig : inlinedMethods) {
			methodSigToId.put(sig, idToSig.size());
			idToSig.add(sig);
		}
		
		this.methodIdToSig = idToSig.toArray(new String[0]);
		if (COUNT_METHODS) {
			maxCommMethods.add(firstNoncommMethod);
			maxInterfaceMethods.add(interfaceGraph == null ? 0 : interfaceGraph.size());
			maxMethods.add(methodSigToId == null ? 0 : methodSigToId.size());
		}
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
	 * Get the method signature of the method with id mid. Use for error reporting.
	 * @param methodUID
	 * @return
	 */
	public String getMethodSignature(final int methodUID) {
		Util.assertTrue(Spec.getModuleID(methodUID) == id, 
				"method id " + methodUID + " (module=" + Spec.getModuleID(methodUID) 
				+ ", method=" + Spec.getMethodID(methodUID) + 
				") is not a member of module " + InstrumentationAgent.sourceName(qualifiedName) + " (id " + id + ")");
		return methodIdToSig[Spec.getMethodID(methodUID)];
	}
	
	@SuppressWarnings("serial")
	public class MethodNotFoundException extends Exception {
		protected final String sig;
		public MethodNotFoundException(String sig) {
			super("Method " + sig + " not found in module " + CompiledModuleSpec.this.getName());
			this.sig = sig;
		}
	}
	
	/**
	 * Get the method ID for the method with signature sig. throws exception
	 * if not found.
	 * @param sig
	 * @return
	 * @throws MethodNotFoundException 
	 */
	public int getMethodUID(final String sig) throws MethodNotFoundException {
	    if (!methodSigToId.containsKey(sig)) {
	        throw new MethodNotFoundException(sig);
	    }
		return Spec.makeUID(id, methodSigToId.get(sig));
	}
	
	/**
	 * Check if communication is allowed from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public boolean isAllowed(final int w, final int r) {
		Util.assertTrue(Spec.getModuleID(w) == id && Spec.getModuleID(r) == id);
		return commGraph.containsEdge(Spec.getMethodID(w), Spec.getMethodID(r));
	}
	public boolean allAllowed(final int w, final BitVectorIntSet readers) {
		Util.assertTrue(Spec.getModuleID(w) == id); //FIXME && Spec.getModuleID(r) == id);
		final BitVectorIntSet edges = commGraph.getOutEdges(Spec.getMethodID(w));
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
		Util.assertTrue(Spec.getModuleID(uid) == id, "method id " + uid + " (module=" + Spec.getModuleID(uid) + ", method=" + Spec.getMethodID(uid) + ") is not a member of module " + InstrumentationAgent.sourceName(qualifiedName) + " (id " + id + ")");
		final int mid = Spec.getMethodID(uid);
		if (mid >= commGraph.size()) {
			if (mid < methodIdToSig.length && mid >= firstInlinedMethod) {
				// if inlined
				return CommunicationKind.INLINE;
			} else {
				// else noncomm
				return CommunicationKind.NONCOMM;
			}
		} else {
			return CommunicationKind.COMM;
		}
		/* else if (uncheckedMethods.contains(mid)) {
		 * return CommunicationKind.UNCHECKED;
		 */
	}
	
	public int numCommMethods() {
		return commGraph.size();
	}
	
	public int numInterfaceMethods() {
		return interfaceGraph.size(); // FIXME
	}
	
	public int numCommEdges() {
		return commGraph.numEdges();
	}
	
	public int numInterfaceEdges() {
		return interfaceGraph.numEdges();
	}
	
	public String toString() {
		String out = "Compiled spec for module " + InstrumentationAgent.sourceName(qualifiedName) + " (ID " + id + ")\n";
		out += "  Methods: " + methodIdToSig.length + "\n";
		out += "    Communicating: " + firstNoncommMethod + "\n";
		for (int i = 0; i < firstNoncommMethod; i++) {
			out += "      " + i + ": " + methodIdToSig[i] + "\n";
		}
		out += "    Non-communicating: " + (firstInlinedMethod - firstNoncommMethod) + "\n";
		for (int i = firstNoncommMethod; i < firstInlinedMethod; i++) {
			out += "      " + i + ": " + methodIdToSig[i] + "\n";
		}
		out += "    Inlined: " + (methodIdToSig.length - firstInlinedMethod) + "\n";
		for (int i = firstInlinedMethod; i < methodIdToSig.length; i++) {
			out += "      " + i + ": " + methodIdToSig[i] + "\n";
		}
		out += "  Communicating Pairs:\n";
		for (Graph.Edge e : commGraph) {
			out += "    " + e.source + ": " + methodIdToSig[e.source] + "  -->  " + e.sink + ": " + methodIdToSig[e.sink] + "\n";
		}
		out += "  Interface Pairs:\n";
		for (Graph.Edge e : interfaceGraph) {
			out += "    " + e.source + ": " + methodIdToSig[e.source] + "  -->  " + e.sink + ": " + methodIdToSig[e.sink] + "\n";
		}
		return out;
	}

	/**
	 * DO NOT MODIFY the array returned.
	 * @return
	 */
	public String[] getMethods() {
		return methodIdToSig; 
	}
	
	public Graph getCommunication() {
		return commGraph;
	}
	public Graph getInterface() {
		return interfaceGraph;
	}
}
