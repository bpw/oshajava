package oshajava.sourceinfo;

public class NullModuleSpec extends ModuleSpec {
    
    private Vector<String> nullMethodIdToSig;
    
    public NullModuleSpec(final String name) {
        this.name = name;
    }
    
    @Override
    public String getMethodSignature(final int methodUID) {
        Util.assertTrue(Spec.getModuleID(methodUID) == id, 
				"method id " + methodUID + " (module=" + Spec.getModuleID(methodUID) 
				+ ", method=" + Spec.getMethodID(methodUID) + 
				") is not a member of module " + name + " (id " + id + ")");
		return nullMethodIdToSig[Spec.getMethodID(methodUID)];
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
            methodSigToId.put(mid, sig);
        }
		final int mid = methodSigToId.get(sig);
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
    public boolean getCommunicationKind(final int uid) {
        Util.assertTrue(Spec.getModuleID(uid) == id, "method id " + uid + " (module=" + Spec.getModuleID(uid) + ", method=" + Spec.getMethodID(uid) + ") is not a member of module " + name + " (id " + id + ")");
        return CommunicationKind.INLINE;
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
