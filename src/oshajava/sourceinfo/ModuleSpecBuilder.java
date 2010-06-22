package oshajava.sourceinfo;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import oshajava.util.ColdStorage;
import oshajava.util.intset.BitVectorIntSet;
import oshajava.support.acme.util.Util;
import oshajava.util.count.Counter;

public class ModuleSpecBuilder implements Serializable {
	
	public static final String EXT = ".omb"; // for Osha Module Builder
	
	public static final boolean DEFAULT_INLINE = true;

	protected final URI uri;
	protected final String qualifiedName;
	
	protected Vector<String> methodIdToSig = new Vector<String>();
	
	protected Map<String, Group> groups = new HashMap<String, Group>();
	protected Vector<String> inlinedMethods = new Vector<String>();
	protected Vector<GroupMembership> memberships = new Vector<GroupMembership>();
	protected Vector<String> nonCommMethods = new Vector<String>();
	
	// For conciseness measurement.
	protected int ctrGroupMembership = 0;
	protected int ctrGroupDeclaration = 0;
	protected int ctrNonComm = 0;
	protected int ctrInline = 0;
	protected int ctrModuleMembership = 0;
	
	public ModuleSpecBuilder(String qualifiedName, URI uri) {
		this.qualifiedName = qualifiedName;
		this.uri = uri;
	}
	
	public void write() throws IOException {
		ColdStorage.store(uri, this);
	}
	
	public String getName() {
		return qualifiedName;
	}
	
	public URI getURI() {
		return uri;
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
	public void addGroup(String id, String[] delegates, String[] merges) {
	    Group g = new Group(id, delegates, merges);
	    groups.put(id, g);
	}
	
	/**
	 * Create a new communication group.
	 */
	public void addInterfaceGroup(String id) {
	    Group g = new Group(id);
	    groups.put(id, g);
	}
	
	/**
	 * Get a group by its unique ID.
	 */
	public Group getGroup(String id) {
	    return groups.get(id);
	}
	
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
		nonCommMethods.add(signature);
        ctrNonComm++;
	}
	/**
	 * Add a method to the list of inlined methods.
	 */
	public void inlineMethod(String signature) {
	    inlinedMethods.add(signature);
	    ctrInline++;
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
	    public String id;
	    public Set<String> delegates = new HashSet<String>();
	    public Set<String> merges = new HashSet<String>();
	    public boolean isInterfaceGroup = false;
	    
	    // Initialize a communication group.
	    public Group(String id, String[] delegates, String[] merges) {
	        this.id = id;
	        for (String delegate : delegates) {
	            this.delegates.add(delegate);
	        }
	        for (String merge : merges) {
	            this.merges.add(merge);
	        }
	    }
	    
	    // Initialize an interface group.
	    public Group(String id) {
	        this.id = id;
	        merges = null;
	        delegates = null;
	        isInterfaceGroup = true;
	    }
	}
	
	/**
	 * Represents that a method is part of a group.
	 */
	protected class GroupMembership implements Serializable {
	    public int methodId;
	    public String groupId;
	    public boolean reader; // otherwise writer
	    
	    public GroupMembership(int methodId, String groupId, boolean reader) {
	        this.methodId = methodId;
	        this.groupId = groupId;
	        this.reader = reader;
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
	
}
