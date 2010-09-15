package oshajava.spec;

import java.util.HashMap;

public class ModuleMap extends SpecFile {
	
	private static final long serialVersionUID = 1L;
	
	public static final String EXT = ".omm";
	
	/**
	 * Maps each method signature to the qualified name of the module it belongs to.
	 */
	private final HashMap<CanonicalName,CanonicalName> map = new HashMap<CanonicalName,CanonicalName>();
	/**
	 * Lists modules whose contents are affected by annotations in the source of this class.
	 */
//	private final HashSet<CanonicalName> affects = new HashSet<CanonicalName>();
	
//	private boolean hasExplicitConstructor = false;

	public ModuleMap(CanonicalName className) {
		super(className);
	}
	
	public void put(CanonicalName method, CanonicalName module) {
//		if (method.contains("<init>")) {
//			hasExplicitConstructor = true;
//		}
		map.put(method, module);
//		affects.add(module);
	}

	public CanonicalName get(CanonicalName method) throws MissingEntryException {
		if (method != null && map.containsKey(method)) {
			return map.get(method);
		} else {
			throw new MissingEntryException(method.toString());
		}
	}
	
//	public Iterable<String> getAffected
	
//	public boolean hasExplicitConstructor() {
//		return hasExplicitConstructor;
//	}
	
	public void clear() {
//		for (Map.Entry<String, String> e : map.entrySet()) {
//			TODO
//		}
		map.clear();
//		affects.clear();
	}
	
	public String toString() {
		return "Modules for methods of class " + getName() + ":\n" + map.toString();
	}
	
	@SuppressWarnings("serial")
	public static class MissingEntryException extends Exception {
		public MissingEntryException(String e) {
			super("Missing entry: " + e);
		}
	}
	
}
