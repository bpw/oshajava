/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.spec;

import oshajava.spec.CompiledModuleSpec.MethodNotFoundException;
import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.MethodDescriptor;
import oshajava.util.BitVectorIntSet;
import oshajava.util.Graph;

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
	
	public ModuleSpec(final CanonicalName name) {
		super(name);
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
	public abstract MethodDescriptor getMethodSignature(final int methodUID);
	
	/**
	 * Get the method ID for the method with signature sig. throws exception
	 * if not found.
	 * @param fullNameAndDesc
	 * @return
	 * @throws MethodNotFoundException 
	 */
	public abstract int getMethodUID(final MethodDescriptor method) throws MethodNotFoundException;
	
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
	
	public abstract MethodDescriptor[] getMethods();
	
	public abstract Graph getCommunication();
	public abstract Graph getInterface();

}
