/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.spec;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import oshajava.runtime.Config;
import oshajava.spec.exceptions.ModuleMapNotFoundException;
import oshajava.spec.exceptions.ModuleSpecNotFoundException;
import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.Descriptor;
import oshajava.spec.names.ObjectTypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.StringMatchResult;
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
	protected static final HashMap<ObjectTypeDescriptor,ModuleMap> classToMap = new HashMap<ObjectTypeDescriptor,ModuleMap>();
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
			final InputStream res = loader.getResourceAsStream(qualifiedName.getInternalName() + CompiledModuleSpec.EXT);
			if (res == null) {
				throw new ModuleSpecNotFoundException(qualifiedName + ", referenced by " + requester);
			}
			final CompiledModuleSpec ms = (CompiledModuleSpec)ColdStorage.load(res);
			if (ms == null) {
				Assert.fail("ModuleSpec %s was null on disk.", qualifiedName);
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
	
	public static synchronized ModuleMap getModuleMap(ObjectTypeDescriptor className, ClassLoader loader) throws ModuleMapNotFoundException {
		if (classToMap.containsKey(className)) {
			return classToMap.get(className);
		}
		if (loader == null) {
			Assert.warn("Bootstrap loader used to load %s.  Trying System loader instead.", className);
	    	loader = ClassLoader.getSystemClassLoader();
		}
		try {
			final InputStream res = loader.getResourceAsStream(className.getInternalName() + ModuleMap.EXT);
			if (res == null) {
				if (Config.noSpecOption.get().test(className.getSourceName()) != StringMatchResult.ACCEPT) {
					Assert.warn("Loader could not find ModuleMap for class %s.", className);
				}
				throw new ModuleMapNotFoundException(className.getTypeName());
			}
			final ModuleMap ms = (ModuleMap)ColdStorage.load(res);
			if (ms == null) {
				Assert.fail("ModuleMap for class %s was null on disk.", className);
				throw new ModuleMapNotFoundException(className.getTypeName());
			}
			classToMap.put(className, ms);
			return ms;
		} catch (IOException e) {
			throw new ModuleMapNotFoundException(className.getTypeName());
		} catch (ClassNotFoundException e) {
			throw new ModuleMapNotFoundException(className.getTypeName());
		}
	}
	
	public static int countModules() {
		return nameToModule.size();
	}
	
	public static Iterable<ModuleSpec> loadedModules() {
		return idToModule;
	}
}
