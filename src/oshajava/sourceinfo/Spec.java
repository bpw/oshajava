package oshajava.sourceinfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Vector;

import oshajava.instrument.InstrumentationAgent;
import oshajava.support.acme.util.Util;
import oshajava.util.ColdStorage;

/**
 * Loading, linking, and mapping of module names and specs.
 * @author bpw
 *
 */
public class Spec {
	
	private static final int METHOD_BITS = 16;
	private static final int METHOD_ID_SELECTOR = (1 << METHOD_BITS) - 1;
	private static final int MAX_MODULE_ID = 1 << (METHOD_BITS) - 1;
//	private static final int MODULE_ID_SELECTOR = MAX_MODULE_ID << METHOD_BITS;

	public static int getMethodID(final int uid) {
		return uid & METHOD_ID_SELECTOR;
	}
	public static int getModuleID(final int uid) {
		return uid >> METHOD_BITS;
	}
	public static int makeUID(final int moduleID, final int methodID) {
		Util.assertTrue(moduleID <= MAX_MODULE_ID && methodID <= METHOD_ID_SELECTOR);
		return (moduleID << METHOD_BITS) | methodID;
	}
	
	/**
	 * Map module name to ModuleSpec.
	 */
	protected static final HashMap<String,ModuleSpec> nameToModule = new HashMap<String,ModuleSpec>();
	
	protected static final Vector<ModuleSpec> idToModule = new Vector<ModuleSpec>();
	
	/**
	 * Load a ModuleSpec from disk given its name.
	 * @param qualifiedName
	 * @return
	 * @throws ModuleSpecNotFoundException if there was a problem finding or loading the spec.
	 */
	protected static ModuleSpec loadModule(String qualifiedName, ClassLoader loader, String requestingClass) throws ModuleSpecNotFoundException {
		try {
			final InputStream res = loader.getResourceAsStream(InstrumentationAgent.internalName(qualifiedName) + ModuleSpec.EXT);
			if (res == null) {
			    // Module spec file legitimately not present. This is a
			    // somewhat questionable decision, but we warn the user
			    // that the module is unspecified and give it a null
			    // (inlined) spec. This is somewhat unsafe, but allows
			    // use of precompiled libraries that don't have
			    // specifications.
			    Util.log("No spec found for " + qualifiedName + ", using null spec.");
			    return new NullModuleSpec(qualifiedName);
			}
			ModuleSpec ms = (ModuleSpec)ColdStorage.load(res);
			if (ms == null) {
			    return new NullModuleSpec(qualifiedName);
			}
			return ms;
		} catch (IOException e) {
			throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requestingClass);
		} catch (ClassNotFoundException e) {
			throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requestingClass);
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
	public static synchronized ModuleSpec getModule(String name, ClassLoader loader, String requestingClass) throws ModuleSpecNotFoundException {
	    if (loader == null) {
	        // Loaded by the JVM.
	        return new NullModuleSpec(name);
	    }
	    
		ModuleSpec module = nameToModule.get(name);
		if (module == null) {
			module = loadModule(name, loader, requestingClass);
//			synchronized (idToModule) { // not needed if this method is synchronized
				module.setId(idToModule.size());
				idToModule.add(module);
//			}
			nameToModule.put(name, module);
		}
		return module;
	}
	public static ModuleSpec getModule(final int uid) {
		return idToModule.get(Spec.getModuleID(uid));
	}
	
//	/**
//	 * Define a new module.
//	 * @param name
//	 * @param module
//	 */
//	public static void defineModule(String name, ModuleSpec module) {
//		Util.assertTrue(!Spec.isDefined(name), "Cannot redefine module '%s'.", name);
//		Util.log(name);
//		nameToModule.put(name, module);
//	}
//	
//	public static boolean isDefined(String name) {
//		return nameToModule.containsKey(name);
//	}
//
//	/**
//	 * Serialize all ModuleSpecs and dump to disk in their own files by name.
//	 * @throws IOException
//	 */
//	public static void dumpModules() throws IOException {
//		for (ModuleSpec m : nameToModule.values()) {
//			// FIXME get the path right. Module "a.b.c.Mod" should be dumped as a file
//			// "Mod.om" in the directory where the contents of package a.b.c are held.
//			ColdStorage.dump(m.getName() + Spec.MODULE_FILE_EXT, m);
//		}
//	}
	
	public static int countModules() {
		return nameToModule.size();
	}
}
