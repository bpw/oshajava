package oshajava.sourceinfo;

import java.io.IOException;
import java.util.HashMap;

import oshajava.util.ColdStorage;

/**
 * Loading, linking, and mapping of module names and specs.
 * @author bpw
 *
 */
public class Spec {
	
	/**
	 * File extension for serialized ModuleSpec storage. OM for Osha Module. 
	 */
	protected static final String MODULE_FILE_EXT = ".om";

	/**
	 * Map module name to ModuleSpec.
	 */
	protected final HashMap<String,ModuleSpec> nameToModule = new HashMap<String,ModuleSpec>();
	
	/**
	 * Load a ModuleSpec from disk given its name.
	 * @param name
	 * @return
	 * @throws ModuleSpecNotFoundException if there was a problem finding or loading the spec.
	 */
	protected ModuleSpec loadModule(String name) throws ModuleSpecNotFoundException {
		try {
			return (ModuleSpec)ColdStorage.load(ClassLoader.getSystemResourceAsStream(name));
		} catch (IOException e) {
			throw new ModuleSpecNotFoundException(name);
		} catch (ClassNotFoundException e) {
			throw new ModuleSpecNotFoundException(name);
		}
	}
	
	/**
	 * Look up a module spec by its qualified name. (Classes should be annotated [by apt or whatever else]
	 * with the qualified name of the module to which they belong. Then we don't need to recompile the spec 
	 * for a library for every program in which it is used.)
	 * @param name
	 * @return
	 * @throws ModuleSpecNotFoundException
	 */
	public ModuleSpec getModule(String name) throws ModuleSpecNotFoundException {
		ModuleSpec module = nameToModule.get(name);
		if (module == null) {
			module = loadModule(name);
			assert module != null;
			nameToModule.put(name, module);
		}
		return module;
	}
}
