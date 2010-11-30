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

import java.util.HashMap;
import java.util.Map;

import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.MethodDescriptor;

public class ModuleMap extends SpecFile {
	
	private static final long serialVersionUID = 1L;
	
	public static final String EXT = ".omm";
	
	/**
	 * Maps each method signature to the qualified name of the module it belongs to.
	 */
	private final HashMap<MethodDescriptor,CanonicalName> map = new HashMap<MethodDescriptor,CanonicalName>();
	/**
	 * Lists modules whose contents are affected by annotations in the source of this class.
	 */
//	private final HashSet<CanonicalName> affects = new HashSet<CanonicalName>();
	
//	private boolean hasExplicitConstructor = false;

	public ModuleMap(CanonicalName classType) {
		super(classType);
	}
	
	public void put(MethodDescriptor method, CanonicalName module) {
//		if (method.contains("<init>")) {
//			hasExplicitConstructor = true;
//		}
		map.put(method, module);
//		affects.add(module);
	}

	public CanonicalName get(MethodDescriptor method) throws MissingEntryException {
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
//		}
		map.clear();
//		affects.clear();
	}
	
	public String toString() {
		String out = "Modules for methods of class " + getName() + ":\n";
		for (Map.Entry<MethodDescriptor, CanonicalName> e : map.entrySet()) {
			out += "  " + e.getKey() + " : " + e.getValue() + "\n";
		}
		return out;
	}
	
	@SuppressWarnings("serial")
	public static class MissingEntryException extends Exception {
		public MissingEntryException(String e) {
			super("Missing entry: " + e);
		}
	}
	
}
