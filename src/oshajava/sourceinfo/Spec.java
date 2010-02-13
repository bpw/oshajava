package oshajava.sourceinfo;

import java.io.IOException;
import java.util.HashMap;

import oshajava.runtime.Stack;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;
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
	protected static final HashMap<String,ModuleSpec> nameToModule = new HashMap<String,ModuleSpec>();
	
	/**
	 * Load a ModuleSpec from disk given its name.
	 * @param name
	 * @return
	 * @throws ModuleSpecNotFoundException if there was a problem finding or loading the spec.
	 */
	protected static ModuleSpec loadModule(String name) throws ModuleSpecNotFoundException {
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
	public static ModuleSpec getModule(String name) throws ModuleSpecNotFoundException {
		ModuleSpec module = nameToModule.get(name);
		if (module == null) {
			module = loadModule(name);
			assert module != null;
			nameToModule.put(name, module);
		}
		return module;
	}
	
	/**
	 * Define a new module.
	 * @param name
	 * @param module
	 */
	public static void defineModule(String name, ModuleSpec module) {
		Util.assertTrue(!nameToModule.containsKey(name));
		nameToModule.put(name, module);
	}

	/**
	 * Serialize all ModuleSpecs and dump to disk in their own files by name.
	 * @throws IOException
	 */
	public static void dumpModules() throws IOException {
		for (ModuleSpec m : nameToModule.values()) {
			// FIXME get the path right. Module "a.b.c.Mod" should be dumped as a file
			// "Mod.om" in the directory where the contents of package a.b.c are held.
			ColdStorage.dump(m.getName() + Spec.MODULE_FILE_EXT, m);
		}
	}
	
	public static int countModules() {
		return nameToModule.size();
	}
}
