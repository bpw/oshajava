package oshajava.spec;

import oshajava.instrument.InstrumentationAgent;
import oshajava.spec.CompiledModuleSpec.MethodNotFoundException;
import oshajava.util.intset.BitVectorIntSet;

public abstract class ModuleSpec extends SpecFile {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * Kinds of communication.
	 * @author bpw
	 *
	 */
	public static enum CommunicationKind { INLINE, NONCOMM, COMM, UNCHECKED } 


	/**
	 * The module id of this module. Only instantiated at runtime.
	 */
	protected transient int id = -1;
	
	public ModuleSpec(final String name) {
		super(InstrumentationAgent.internalName(name));
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
	public abstract String getMethodSignature(final int methodUID);
	
	/**
	 * Get the method ID for the method with signature sig. throws exception
	 * if not found.
	 * @param sig
	 * @return
	 * @throws MethodNotFoundException 
	 */
	public abstract int getMethodUID(final String sig) throws MethodNotFoundException;
	
	/**
	 * Check if communication is allowed from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public abstract boolean isAllowed(final int w, final int r);
	public abstract boolean allAllowed(final int w, final BitVectorIntSet readers);
	
	/**
	 * Check if communication is visible from method with ID w to method with ID r.
	 * @param w
	 * @param r
	 * @return
	 */
	public abstract boolean isPublic(final int w, final int r);
	
	/**
	 * Get the kind of communication in which method with id mid is involved. Options are
	 * INLINE, NONE, READ_ONLY, WRITE_ONLY, BOTH, and maybe UNCHECKED.
	 * @param mid
	 * @return
	 */
	public abstract CommunicationKind getCommunicationKind(final int uid);
	
	public abstract int numCommMethods();
	
	public abstract int numInterfaceMethods();
	
	public abstract int numCommEdges();
	
	public abstract int numInterfaceEdges();
	
	public abstract String toString();
	
	public abstract String[] getMethods();
	
	public abstract Graph getCommunication();
	public abstract Graph getInterface();

}
