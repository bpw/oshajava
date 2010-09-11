package oshajava.sourceinfo;

import java.util.HashMap;

public class ModuleMap extends SpecFile {
	
	private static final long serialVersionUID = 1L;
	
	public static final String EXT = ".omm";
	
	private final HashMap<String,String> map = new HashMap<String,String>();

	public ModuleMap(String className) {
		super(className);
	}
	
	public void put(String method, String module) {
		map.put(method, module);
	}

	public String get(String method) throws MissingEntryException {
		if (method != null && map.containsKey(method)) {
			return map.get(method);
		} else {
			throw new MissingEntryException(method.toString());
		}
	}
	
	public void clear() {
		map.clear();
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
