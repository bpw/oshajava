package oshajava.sourceinfo;

import java.util.Vector;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import oshajava.util.intset.BitVectorIntSet;

public class ModuleSpecBuilder {
	
	private String name;
	
	private Vector<String> methodIdToSig = new Vector<String>();
	
	private Map<String, Group> groups = new HashMap<String, Group>();
	private BitVectorIntSet inlinedMethods = new BitVectorIntSet();
	
	public ModuleSpecBuilder(String name) {
		this.name = name;
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
	    getGroup(groupId).readers.add(idForSig(signature));
	}
	
	/**
	 * Add a method to a group as a writer.
	 */
	public void addWriter(String groupId, String signature) {
	    getGroup(groupId).writers.add(idForSig(signature));
	}
	
	/**
	 * Add a method to the list of inlined methods.
	 */
	public void inlineMethod(String signature) {
	    inlinedMethods.add(idForSig(signature));
	}
	
	/**
	 * Returns a (static) ModuleSpec object reflecting this module.
	 */
	public ModuleSpec generateSpec() {
	    Graph internalGraph = new Graph(methodIdToSig.size());
	    Graph interfaceGraph = new Graph(methodIdToSig.size());
	    for (int source=0; source<methodIdToSig.size(); ++source) {
	        
	        BitVectorIntSet destinations = new BitVectorIntSet();
	        for (Group g : groups.values()) {
	            if (g.writers.contains(source)) {
	                
	                BitVectorIntSet outSet;
	                if (g.isInterfaceGroup) {
	                    outSet = internalGraph.getOutEdges(source);
	                } else {
	                    outSet = interfaceGraph.getOutEdges(source);
	                } 
	                
	                for (Integer destination : g.readers) {
	                    outSet.add(destination);
	                }
	                
	            }
	        }
	        
	    }
	    
	    //TODO
	    
	    HashMap<String, Integer> methodSigToId = new HashMap<String, Integer>();
	    for (int i=0; i<methodSigToId.size(); ++i) {
	        methodSigToId.put(methodIdToSig.get(i), i);
	    }
	    
	    return new ModuleSpec(
	        name,
	        methodIdToSig.toArray(new String[0]),
	        internalGraph,
	        interfaceGraph,
	        inlinedMethods,
	        methodSigToId
	    );
	}
	
	/**
	 * Represents a communication or interface group.
	 */
	private class Group {
	    public String id;
	    public Set<Integer> readers = new HashSet<Integer>();
	    public Set<Integer> writers = new HashSet<Integer>();
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
	
}
