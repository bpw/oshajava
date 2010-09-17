package oshajava.spec;

import java.util.HashMap;
import java.util.Vector;

import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.MethodDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.util.BitVectorIntSet;
import oshajava.util.Graph;

public class NullModuleSpec extends ModuleSpec {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected Vector<MethodDescriptor> nullMethodIdToSig = new Vector<MethodDescriptor>();
    
	/**
	 * Map from method signature to id.
	 */
	protected final HashMap<MethodDescriptor,Integer> methodSigToId = new HashMap<MethodDescriptor,Integer>();
	
	public NullModuleSpec(final CanonicalName name) {
        super(name);
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
        return true;
    }
    
    @Override
    public boolean allAllowed(final int w, final BitVectorIntSet readers) {
        return true;
    }
    
    @Override
    public boolean isPublic(final int w, final int r) {
        return true;
    }
    
    @Override
    public CommunicationKind getCommunicationKind(final int uid) {
        Assert.assertTrue(Spec.getModuleID(uid) == id, "method id " + uid + " (module=" + Spec.getModuleID(uid) + 
        		", method=" + Spec.getMethodID(uid) + ") is not a member of module " + getName()  + " (id " + id + ")");
        return CompiledModuleSpec.CommunicationKind.INLINE;
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
