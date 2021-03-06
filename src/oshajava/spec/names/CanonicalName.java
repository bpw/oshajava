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

package oshajava.spec.names;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import oshajava.support.acme.util.Assert;

/**
 * Structured storage of canonical names packages, types, modules, etc. $ (not .) is the delimiter for nested classes...
 */
public class CanonicalName extends Name {
	private static final long serialVersionUID = 1L;
	// FIXME $ is a valid character in class names.
	private final String pkg, simple;
	private ObjectTypeDescriptor type;
	
	@Override
	public int hashCode() {
		return (pkg == null ? 0 : pkg.hashCode()) ^ simple.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		return other instanceof CanonicalName && (pkg == null ? ((CanonicalName)other).pkg == null : pkg.equals(((CanonicalName)other).pkg)) 
			&& simple.equals(((CanonicalName)other).simple);
	}
	
	/**
	 * Construct a new CanonicalName from the given package and simple names.
	 * @param sourcePackage The package name, using '.' as a delimiter.
	 * @param sourceSimple The simple name, using '.' and '$' as delimiters.
	 */
	private CanonicalName(final String sourcePackage, final String sourceSimple) {
		this.pkg = sourcePackage == null || sourcePackage.isEmpty() ? null : sourcePackage;
		this.simple = sourceSimple;
		verify();
	}
	
	/**
	 * Verify that the package and simple names are well-formed.  Call this at the end of each constructor.
	 */
	private void verify() {
		Assert.assertTrue(pkg == null || !pkg.isEmpty(), "Empty package should be null.");
		Assert.assertTrue(pkg == null || (!pkg.replace(".", "").isEmpty() && !pkg.startsWith(".") && !pkg.endsWith(".")), "Malformed package: %s", pkg);
		Assert.assertTrue(simple != null, "Null simple name!");
		Assert.assertTrue(!simple.isEmpty() && !simple.startsWith(".") && !simple.endsWith(".") && !simple.startsWith("/") && !simple.endsWith("/"), "Malformed simple name.");
//		System.out.println(this);
	}
	
	/**
	 * @return The package component of the name as a JVM internal format String.
	 */
	public String getPackage() {
		return pkg == null ? "" : pkg.replace('.', '/');
	}
	
	/**
	 * @return The package component of the name as a Java source format String.
	 */
	public String getSourcePackage() {
		return pkg == null ? "" : pkg;
	}
	
	/**
	 * @return The simple name component of the name as a JVM internal format String.
	 */
	public String getSimpleName() {
		return simple.replace('.', '$');
	}
	
	/**
	 * @return The simple name component of the name as a Java source format String.
	 */
	public String getSourceSimpleName() {
		return simple;
	}
	
	/**
	 * @return The source format String for the full name.
	 */
	public String getSourceName() {
		if (pkg == null) {
			return getSourceSimpleName();
		} else {
			return getSourcePackage() + '.' + getSourceSimpleName();
		}
	}
	
	/**
	 * @return The internal format String for the full name.
	 */
	public String getInternalName() {
		if (pkg == null) {
			return getSimpleName();
		} else {
			return getPackage() + '/' + getSourceSimpleName();
		}
	}
	
	public synchronized ObjectTypeDescriptor getType() {
		if (type == null) {
			type = new ObjectTypeDescriptor(this);
		}
		return type;
	}
	
	/******************************************************/
	
	private static final Map<TypeElement,CanonicalName> elementToName = new HashMap<TypeElement,CanonicalName>();
	
	public static CanonicalName of(final TypeElement type, final Elements util) {
		synchronized (elementToName) {
			if (elementToName.containsKey(type)) {
				return elementToName.get(type);
			} else {
				String qn = type.getQualifiedName().toString();
				String p = util.getPackageOf(type).getQualifiedName().toString();
				final CanonicalName name = new CanonicalName(p, (p == null || p.isEmpty() ? qn : qn.substring(p.length() + 1)));
				elementToName.put(type, name);
				return name;
			}
		}
	}
	
	private static final Map<String,CanonicalName> fullToName = new HashMap<String,CanonicalName>();
	
	public static CanonicalName of(String pkg, final String simple) {
		if (pkg == null) pkg = "";
		pkg = pkg.replace('/', '.');
		String fullName = pkg + '.' + simple;
		synchronized (fullToName) {
			if (fullToName.containsKey(fullName)) {
				return fullToName.get(fullName);
			} else {
				final CanonicalName name = new CanonicalName(pkg, simple);
				fullToName.put(fullName, name);
				return name;
			}
		}
	}
	

	/**
	 * Takes names of the form a.b.C$D$E or a/b/C$D$E, but NOT a.b.C.D.E or a/b/C.D.E
	 * @param fullName
	 * @return
	 */
	public static CanonicalName of(String fullName) {
		fullName = fullName.replace('/', '.');
		synchronized (fullToName) {
			if (fullToName.containsKey(fullName)) {
				return fullToName.get(fullName);
			} else {
				int i = fullName.lastIndexOf('.');
				String pkg = i == -1 ? null : fullName.substring(0, i);
				String simple = fullName.substring(i + 1);
				final CanonicalName name = new CanonicalName(pkg, simple);
				fullToName.put(fullName, name);
				return name;
			}
		}
	}

}
