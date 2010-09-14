package oshajava.sourceinfo;

import java.util.HashMap;
import java.util.Map;

import oshajava.sourceinfo.Group.DuplicateMethodException;

/**
 * A compile-time representation of a module.
 */
public class Module extends SpecFile {
	
	private static final long serialVersionUID = 3L;

	public static final String EXT = ".omi"; // Osha Module Incremental
	
	public static boolean DEFAULT_INLINE;
	
	public static void setDefaultInline(boolean b) {
		DEFAULT_INLINE = b;
	}
	
	/****************************/
	
	@SuppressWarnings("serial")
	static class DuplicateGroupException extends Exception {
		protected final Module module;
		protected final Group sig;
		public DuplicateGroupException(Module module, Group group) {
			super((group.kind() == Group.Kind.COMM ? "Communication" : "Interface") + " Group \"" + group + "\" is already entered in module " + module + ".");
			this.module = module;
			this.sig = group;
		}
	}
	
	@SuppressWarnings("serial")
	static class DuplicateMethodException extends Exception {
		protected final Module module;
		protected final String sig;
		public DuplicateMethodException(Module module, String sig) {
			super("Method " + sig + " is already entered in module " + module + ".");
			this.module = module;
			this.sig = sig;
		}
		public String getMethod() {
			return sig;
		}
		public Module getModule() {
			return module;
		}
	}
	
	@SuppressWarnings("serial")
	static class GroupNotFoundException extends Exception {
		protected final String group;
		public GroupNotFoundException(String group) {
			super("Group \"" + group + "\" not found.");
			this.group = group;
		}
	}
	
	protected Map<String,MethodSpec> methodSpecs = new HashMap<String,MethodSpec>();
	protected Map<String,Group> groups = new HashMap<String,Group>();
	
	public Module(String qualifiedName) {
		super(qualifiedName);
	}
	
	/**
	 * Add a communication group to the method.
	 * @param group Group name
	 * @throws DuplicateGroupException if a group (communication or interface) with the same name already exists in this module.
	 */
	public void addCommGroup(String group) throws DuplicateGroupException {
		if (groups.containsKey(group)) {
			throw new DuplicateGroupException(this, groups.get(group));
		}
		groups.put(group, new Group(Group.Kind.COMM, group));
	}
	
	/**
	 * Add an interface group to the method.
	 * @param group Group name
	 * @throws DuplicateGroupException if a group (communication or interface) with the same name already exists in this module.
	 */
	public void addInterfaceGroup(String group) throws DuplicateGroupException {
		if (groups.containsKey(group)) {
			throw new DuplicateGroupException(this, groups.get(group));
		}
		groups.put(group, new Group(Group.Kind.INTERFACE, group));
	}
	
	/**
	 * Retrieve the named group.
	 * @param group Name of the group to retrieve.
	 * @return The Group.
	 * @throws GroupNotFoundException if no group with this name is found in this module.
	 */
	public Group getGroup(String group) throws GroupNotFoundException {
		if (!groups.containsKey(group)) {
			throw new GroupNotFoundException(group);
		}
		return groups.get(group);
	}
	
	/**
	 * Add a method with the given individual specification to the module.
	 * @param sig Method signature
	 * @param spec MethodSpec for the method.
	 * @throws DuplicateMethodException if a method with the same signature already exists in this module.
	 * @throws Group.DuplicateMethodException 
	 */
	public void addMethod(String sig, MethodSpec spec) throws DuplicateMethodException, Group.DuplicateMethodException {
		if (methodSpecs.containsKey(sig)) {
			throw new DuplicateMethodException(this, sig);
		}
		methodSpecs.put(sig, spec);
		for (Group g : spec.readGroups()) {
			g.addReader(sig);
		}
		for (Group g : spec.writeGroups()) {
			g.addWriter(sig);
		}
	}
	
	public CompiledModuleSpec generateSpec() {
		return new CompiledModuleSpec(getName(), methodSpecs);
	}
	
	public String toString() {
		return generateSpec().toString();
	}
	
}
