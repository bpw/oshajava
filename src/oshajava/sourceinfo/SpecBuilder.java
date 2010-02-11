package oshajava.sourceinfo;

import java.io.IOException;

import oshajava.support.acme.util.Util;
import oshajava.util.ColdStorage;

/**
 * For building a Spec. Not used at runtime, just compile time.
 * @author bpw
 *
 */
public class SpecBuilder extends Spec {
	
	/**
	 * Define a new module.
	 * @param name
	 * @param module
	 */
	public void defineModule(String name, ModuleSpec module) {
		Util.assertTrue(!nameToModule.containsKey(name));
		nameToModule.put(name, module);
	}

	/**
	 * Serialize all ModuleSpecs and dump to disk in their own files by name.
	 * @throws IOException
	 */
	public void dumpModules() throws IOException {
		for (ModuleSpec m : nameToModule.values()) {
			// FIXME get the path right. Module "a.b.c.Mod" should be dumped as a file
			// "Mod.om" in the directory where the contents of package a.b.c are held.
			ColdStorage.dump(m.getName() + Spec.MODULE_FILE_EXT, m);
		}
	}
}
