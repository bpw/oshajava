package oshajava.sourceinfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ModuleMap extends SpecFile {
	
	private static final long serialVersionUID = 1L;
	
	public static final String EXT = ".omm";
	
	/**
	 * Maps each method signature to the qualified name of the module it belongs to.
	 */
	private final HashMap<String,String> map = new HashMap<String,String>();
	/**
	 * Lists modules whose contents are affected by annotations in the source of this class.
	 */
	private final HashSet<String> affects = new HashSet<String>();

	public ModuleMap(String className) {
		super(className);
	}
	
	public void put(String method, String module) {
		map.put(method, module);
		affects.add(module);
	}

	public String get(String method) throws MissingEntryException {
		if (method != null && map.containsKey(method)) {
			return map.get(method);
		} else {
			throw new MissingEntryException(method.toString());
		}
	}
	
	public void clear() {
		for (Map.Entry<String, String> e : map.entrySet()) {
//			e.
		}
		map.clear();
		affects.clear();
	}
	
	public String toString() {
		return qualifiedName + EXT + ":\n" + map.toString();
	}
	
	@SuppressWarnings("serial")
	public static class MissingEntryException extends Exception {
		public MissingEntryException(String e) {
			super("Missing entry: " + e);
		}
	}
	
}
