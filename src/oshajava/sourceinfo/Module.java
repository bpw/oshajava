package oshajava.sourceinfo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import oshajava.support.acme.util.Util;
import oshajava.util.intset.BitVectorIntSet;

public class Module extends SpecFile {
	
	private static final long serialVersionUID = 2L;

	public static final String EXT = ".omi"; // Osha Module Incremental
	
	public static boolean DEFAULT_INLINE;
	
	public static void setDefaultInline(boolean b) {
		DEFAULT_INLINE = b;
	}

	protected Vector<String> methodIdToSig = new Vector<String>();
	
	protected Map<String, Group> groups = new HashMap<String, Group>();
	protected Set<String> inlinedMethods = new HashSet<String>();
	protected Set<GroupMembership> memberships = new HashSet<GroupMembership>();
	protected Set<String> nonCommMethods = new HashSet<String>();
	
	// For conciseness measurement.
	protected int ctrGroupMembership = 0;
	protected int ctrGroupDeclaration = 0;
	protected int ctrNonComm = 0;
	protected int ctrInline = 0;
	protected int ctrModuleMembership = 0;
	
	public Module(String qualifiedName) {
		super(qualifiedName);
	}
	
	/**
	 * Get an id for the method indicated. If no id is assigned yet, one
	 * is assigned.
	 */
	private int idForSig(String signature) {
	    int id = methodIdToSig.indexOf(signature);
	    if (id != -1) {
	        // Found.
	        return id;
	    }
	    // Not found.
	    methodIdToSig.add(signature);
	    return methodIdToSig.size() - 1;
	}
	
	/**
	 * Create a new communication group.
	 */
	public void addGroup(String id) {
	    Group g = new Group(id, false);
	    groups.put(id, g);
	}
	
	/**
	 * Create a new communication group.
	 */
	public void addInterfaceGroup(String id) {
	    Group g = new Group(id, true);
	    groups.put(id, g);
	}
	
	/**
	 * Get a group by its unique ID.
	 */
	public Group getGroup(String id) {
	    return groups.get(id);
	}
	
//	public void addMethod(String signature, MethodSpec s) {
//		
//	}
	
	/**
	 * Add a method to a group as a reader.
	 */
	public void addReader(String groupId, String signature) {
	    memberships.add(new GroupMembership(
	        idForSig(signature),
	        groupId,
	        true
	    ));
	}
	
	/**
	 * Add a method to a group as a writer.
	 */
	public void addWriter(String groupId, String signature) {
	    memberships.add(new GroupMembership(
	        idForSig(signature),
	        groupId,
	        false
	    ));
	}
	
	/**
	 * Add a method as a non-communicator.
	 */
	public void addNonComm(String signature) {
		if (!nonCommMethods.contains(signature)) {
			nonCommMethods.add(signature);
			ctrNonComm++;
		}
	}
	/**
	 * Add a method to the list of inlined methods.
	 */
	public void inlineMethod(String signature) {
		if (!inlinedMethods.contains(signature)) {
			inlinedMethods.add(signature);
			ctrInline++;
		}
	}

	/**
	 * Add an unannotated method (DEFAULT)
	 */
	public void addUnannotatedMethod(String signature) {
    	(DEFAULT_INLINE ? inlinedMethods : nonCommMethods).add(signature);
 	}
	
	/**
	 * Returns a (static) ModuleSpec object reflecting this module.
	 */
	public ModuleSpec generateSpec() {
		Graph internalGraph = new Graph(methodIdToSig.size());
		internalGraph.fill();
		Graph interfaceGraph = new Graph(methodIdToSig.size());
		interfaceGraph.fill();

		// Put in edges.
		for (GroupMembership readerMem : memberships) {
		    if (readerMem.reader) {		
    		    final Group group = getGroup(readerMem.groupId);
    		    if (group == null) {
    		        Util.fail("group " + readerMem.groupId + " not found in module " + qualifiedName);
    		    }
    			final Graph graph = group.isInterfaceGroup ? interfaceGraph : internalGraph;
    		    for (GroupMembership writerMem : memberships) {
    		        if (!writerMem.reader && writerMem.groupId.equals(group.id)) {
        		        graph.addEdge(writerMem.methodId, readerMem.methodId);
    		        }
    		    }
    	    }
		}
		
		Vector<String> specIds = new Vector<String>();
		specIds.addAll(methodIdToSig);
        // Give inlined and noncomm methods higher IDs.
		for (String s: nonCommMethods) {
			specIds.add(s);
		} // noncomm must comm before inlined b/c of how used below.
		final int firstInlinedID = specIds.size();
		for (String s : inlinedMethods) {
			specIds.add(s);
		}
		
		// map sigs to ids.
		final HashMap<String, Integer> methodSigToId = new HashMap<String, Integer>();
		for (int i = 0; i < specIds.size(); ++i) {
	        methodSigToId.put(specIds.get(i), i);
	    }
		
		final BitVectorIntSet inlined = new BitVectorIntSet();
		for (int i = firstInlinedID; i < specIds.size(); i++) {
			inlined.add(i);
		}
		
//	    Util.log(methodSigToId);
	    return new ModuleSpec(
	        qualifiedName,
	        specIds.toArray(new String[0]),
	        methodIdToSig.size(),
	        internalGraph,
	        interfaceGraph,
	        inlined,
	        methodSigToId
	    );
	}
	
	/**
	 * Represents a communication or interface group.
	 */
	protected class Group implements Serializable {
	    /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public String id;
	    public boolean isInterfaceGroup = false;
	    
	    // Initialize an interface group.
	    public Group(String id, boolean isInterfaceGroup) {
	        this.id = id;
	        this.isInterfaceGroup = isInterfaceGroup;
	    }
	}
	
	/**
	 * Represents that a method is part of a group.
	 */
	protected class GroupMembership implements Serializable {
	    /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public int methodId;
	    public String groupId;
	    public boolean reader; // otherwise writer
	    
	    public GroupMembership(int methodId, String groupId, boolean reader) {
	        this.methodId = methodId;
	        this.groupId = groupId;
	        this.reader = reader;
	    }
	    
	    @Override
	    public int hashCode() {
	    	return methodId ^ groupId.hashCode() & (reader ? 0 : 1);
	    }
	    @Override
	    public boolean equals(Object other) {
	    	return other != null && other instanceof GroupMembership && this.methodId == ((GroupMembership)other).methodId && this.groupId.equals(((GroupMembership)other).groupId) && this.reader == ((GroupMembership)other).reader;
	    }
	}
	
	/**
	 * Statistics about the module.
	 */
	public String summary() {
	    String out = "";
	    
	    int interfaceGroups = 0;
	    for (Group g : groups.values()) {
	        if (g.isInterfaceGroup) {
	            interfaceGroups++;
	        }
	    }
	    out += groups.size() + " groups";
	    if (interfaceGroups > 0) {
    	    out += " (" + (groups.size() - interfaceGroups) + " communication, ";
    	    out += interfaceGroups + " interface), ";
        } else {
            out += ", ";
        }
	    
	    out += methodIdToSig.size() + nonCommMethods.size() + " non-inlined methods (" + methodIdToSig.size() + " comm., " + nonCommMethods.size() + " non-comm.), ";
	    out += inlinedMethods.size() + " inlined";
	    
	    return out;
	}
	
	public String toString() {
		return generateSpec().toString();
	}
	
}
