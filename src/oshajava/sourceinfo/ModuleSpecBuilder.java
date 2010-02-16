package oshajava.sourceinfo;

import java.util.Vector;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

public class ModuleSpecBuilder {
	
	private String name;
	
	private Vector<String> methodIdToSig = new Vector<String>();
	
	private Map<String, Group> groups = new HashMap<String, Group>();
	private Set<Integer> inlinedMethods = new HashSet<Integer>();
	
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
	 * Create a new group.
	 */
	public void addGroup(String id, String[] delegates, String[] merges) {
	    Group g = new Group(id, delegates, merges);
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
	    return null;
	}
	
	/**
	 * Represents a communication group.
	 */
	private class Group {
	    public String id;
	    public Set<Integer> readers = new HashSet<Integer>();
	    public Set<Integer> writers = new HashSet<Integer>();
	    public Set<String> delegates = new HashSet<String>();
	    public Set<String> merges = new HashSet<String>();
	    
	    public Group(String id, String[] delegates, String[] merges) {
	        this.id = id;
	        for (String delegate : delegates) {
	            this.delegates.add(delegate);
	        }
	        for (String merge : merges) {
	            this.merges.add(merge);
	        }
	    }
	}
	
}
