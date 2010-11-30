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

import java.util.HashMap;
import java.util.Vector;

import oshajava.runtime.Config;
import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.MethodDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.util.BitVectorIntSet;
import oshajava.util.Graph;

public class NullModuleSpec extends ModuleSpec {
    
	private static final long serialVersionUID = 1L;
	
	private static final boolean ALLOW = Config.noSpecActionOption.get() != Config.DefaultSpec.NONCOMM;
	
	public static final ModuleSpec MODULE = new NullModuleSpec();

	protected final Vector<MethodDescriptor> nullMethodIdToSig = new Vector<MethodDescriptor>();
    
	/**
	 * Map from method signature to id.
	 */
	protected final HashMap<MethodDescriptor,Integer> methodSigToId = new HashMap<MethodDescriptor,Integer>();
	
	private NullModuleSpec() {
        super(CanonicalName.of("__NullModuleSpec__"));
    }
    
    @Override
    public MethodDescriptor getMethodSignature(final int methodUID) {
        Assert.assertTrue(Spec.getModuleID(methodUID) == id, 
				"method id " + methodUID + " (module=" + Spec.getModuleID(methodUID) 
				+ ", method=" + Spec.getMethodID(methodUID) + 
				") is not a member of module " + getName() + " (id " + id + ")");
		return nullMethodIdToSig.get(Spec.getMethodID(methodUID));
    }
    
    @Override
    public int getMethodUID(final MethodDescriptor sig) {
        int mid;
        if (methodSigToId.containsKey(sig)) {
            mid = methodSigToId.get(sig);
        } else {
            // Assign a new id.
            nullMethodIdToSig.add(sig);
            mid = nullMethodIdToSig.size()-1;
            methodSigToId.put(sig, mid);
        }
		return Spec.makeUID(id, mid);
	}
    
    @Override
    public boolean isAllowed(final int w, final int r) {
        return ALLOW;
    }
    
    @Override
    public boolean allAllowed(final int w, final BitVectorIntSet readers) {
        return ALLOW;
    }
    
    @Override
    public boolean isPublic(final int w, final int r) {
        return true;
    }
    
    @Override
    public CommunicationKind getCommunicationKind(final int uid) {
        Assert.assertTrue(Spec.getModuleID(uid) == id, "method id " + uid + " (module=" + Spec.getModuleID(uid) + 
        		", method=" + Spec.getMethodID(uid) + ") is not a member of module " + getName()  + " (id " + id + ")");
        return ALLOW ? CompiledModuleSpec.CommunicationKind.INLINE : CompiledModuleSpec.CommunicationKind.NONCOMM;
    }
    
    @Override
	public int numCommMethods() {
		return 0;
	}
	
    @Override
	public int numInterfaceMethods() {
		return 0;
	}
	
    @Override
	public int numCommEdges() {
		return 0;
	}
	
    @Override
	public int numInterfaceEdges() {
		return 0;
	}
	
    @Override
    public String toString() {
        return "Module " + getName()  + " [null module] (ID " + id + ")";
    }
    
	public MethodDescriptor[] getMethods() {
		MethodDescriptor[] s = new MethodDescriptor[nullMethodIdToSig.size()];
		for (int i = 0; i < s.length; i++) {
			s[i] = nullMethodIdToSig.get(i);
		}
		return s;
	}
	
	public Graph getCommunication() {
		return new Graph(0);
	}
	public Graph getInterface() {
		return new Graph(0);
	}

}
