package oshajava.spec;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import oshajava.spec.exceptions.ModuleMapNotFoundException;
import oshajava.spec.exceptions.ModuleSpecNotFoundException;
import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.Descriptor;
import oshajava.support.acme.util.Assert;
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
		Assert.assertTrue(moduleID <= MAX_MODULE_ID && methodID <= METHOD_ID_SELECTOR);
		return (moduleID << METHOD_BITS) | methodID;
	}
	
	/**
	 * Map module name to ModuleSpec.
	 */
	protected static final HashMap<CanonicalName,ModuleSpec> nameToModule = new HashMap<CanonicalName,ModuleSpec>();
	protected static final HashMap<CanonicalName,ModuleMap> classToMap = new HashMap<CanonicalName,ModuleMap>();
	protected static final ArrayList<ModuleSpec> idToModule = new ArrayList<ModuleSpec>();
	
	/**
	 * Load a ModuleSpec from disk given its name.
	 * @param qualifiedName
	 * @return
	 * @throws ModuleSpecNotFoundException if there was a problem finding or loading the spec.
	 */
	protected static ModuleSpec loadModule(CanonicalName qualifiedName, ClassLoader loader, Descriptor requester) throws ModuleSpecNotFoundException {
	    if (loader == null) {
	    	Assert.warn("Bootstrap loader used to load %s.  Trying System loader instead.", requester);
	    	loader = ClassLoader.getSystemClassLoader();
	    }
		try {
			final InputStream res = loader.getResourceAsStream(qualifiedName.toInternalString() + CompiledModuleSpec.EXT);
			if (res == null) {
				throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requester);
			}
			final CompiledModuleSpec ms = (CompiledModuleSpec)ColdStorage.load(res);
			if (ms == null) {
				Assert.warn("ModuleSpec %s was null on disk.", qualifiedName);
				throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requester);
			}
			return ms;
		} catch (IOException e) {
			throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requester);
		} catch (ClassNotFoundException e) {
			throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requester);
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
	public static synchronized ModuleSpec getModule(CanonicalName name, ClassLoader loader, Descriptor requester) throws ModuleSpecNotFoundException {
	    ModuleSpec module = nameToModule.get(name);
		if (module == null) {
			module = loadModule(name, loader, requester);
			module.setId(idToModule.size());
			idToModule.add(module);
			nameToModule.put(name, module);
		}
		return module;
	}
	public static ModuleSpec getModule(final int uid) {
		return idToModule.get(Spec.getModuleID(uid));
	}
	
	public static synchronized ModuleMap getModuleMap(CanonicalName className, ClassLoader loader) throws ModuleMapNotFoundException {
		if (classToMap.containsKey(className)) {
			return classToMap.get(className);
		}
		if (loader == null) {
			Assert.warn("Bootstrap loader used to load %s.  Trying System loader instead.", className);
	    	loader = ClassLoader.getSystemClassLoader();
		}
		try {
			final InputStream res = loader.getResourceAsStream(className.toInternalString() + ModuleMap.EXT);
			if (res == null) {
				Assert.warn("Loader could not find ModuleMap for class %s.", className);
				throw new ModuleMapNotFoundException(className);
			}
			final ModuleMap ms = (ModuleMap)ColdStorage.load(res);
			if (ms == null) {
				Assert.warn("ModuleMap for class %s was null on disk.", className);
				throw new ModuleMapNotFoundException(className);
			}
			classToMap.put(className, ms);
			return ms;
		} catch (IOException e) {
			throw new ModuleMapNotFoundException(className);
		} catch (ClassNotFoundException e) {
			throw new ModuleMapNotFoundException(className);
		}
	}
	
	public static int countModules() {
		return nameToModule.size();
	}
	
	public static Iterable<ModuleSpec> loadedModules() {
		return idToModule;
	}
}
