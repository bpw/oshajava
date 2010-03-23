package oshajava.sourceinfo;

import java.util.Vector;
import java.util.HashMap;
import oshajava.util.intset.BitVectorIntSet;
import oshajava.support.acme.util.Util;

public class NullModuleSpec extends ModuleSpec {
    
    protected Vector<String> nullMethodIdToSig = new Vector<String>();
    
    public NullModuleSpec(final String name) {
        super(name, null, 0, null, null, null,
              new HashMap<String,Integer>());
    }
    
    @Override
    public String getMethodSignature(final int methodUID) {
        Util.assertTrue(Spec.getModuleID(methodUID) == id, 
				"method id " + methodUID + " (module=" + Spec.getModuleID(methodUID) 
				+ ", method=" + Spec.getMethodID(methodUID) + 
				") is not a member of module " + name + " (id " + id + ")");
		return nullMethodIdToSig.get(Spec.getMethodID(methodUID));
    }
    
    @Override
    public int getMethodUID(final String sig) {
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
        Util.assertTrue(Spec.getModuleID(uid) == id, "method id " + uid + " (module=" + Spec.getModuleID(uid) + ", method=" + Spec.getMethodID(uid) + ") is not a member of module " + name + " (id " + id + ")");
        return ModuleSpec.CommunicationKind.INLINE;
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
    public void describe() {
        System.out.println(name + " (null group)");
    }
    
    @Override
    public boolean checkIntegrity() {
        return true;
    }
    
}
