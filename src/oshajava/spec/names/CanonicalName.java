package oshajava.spec.names;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Structured storage of canonical names packages, types, modules, etc.
 */
public class CanonicalName extends Name {
	private static final long serialVersionUID = 1L;
	
	private final String pkg, simple;
	private ObjectTypeDescriptor type;
	
	/**
	 * Construct a new CanonicalName from the given package and simple names.
	 * @param pkg The package name, using '.' or '/' as a delimiter.
	 * @param simple The simple name, using '.' or '$' as a delimiter.
	 */
	private CanonicalName(final String pkg, String simple) {
		this.pkg = pkg == null || pkg.isEmpty() ? null : pkg.replace('.', '/');
		this.simple = simple.replace('.', '$');
		verify();
	}
	
	/**
	 * Construct a new CanonicalName for the given TypeElement, using the Elements utility.
	 * @param type
	 * @param util
	 */
	private CanonicalName(final TypeElement type, final Elements util) {
		String qn = type.getQualifiedName().toString();
		String p = util.getPackageOf(type).getQualifiedName().toString().replace('.', '/');
		this.pkg = p == null || p.isEmpty() ? null : p;
		this.simple = (pkg == null ? qn : qn.substring(pkg.length() + 1)).replace('.', '$');
		verify();
	}
	
	/**
	 * Construct a new CanonicalName from a single String.  All dots '.' and slashes '/' are 
	 * assumed to be package delimiters.  All dollars '$' are assumed to be inner class delimiters.
	 * @param qualifiedName
	 */
	private CanonicalName(String qualifiedName) {
		qualifiedName = qualifiedName.replace('.', '/');
		int i = qualifiedName.lastIndexOf('/');
		this.pkg = i == -1 ? null : qualifiedName.substring(0, i);
		this.simple = qualifiedName.substring(i + 1);
		verify();
	}
	
	/**
	 * Verify that the package and simple names are well-formed.  Call this at the end of each constructor.
	 */
	private void verify() {
		if (pkg != null && (pkg.endsWith(".") || this.pkg.endsWith("/"))) {
			throw new RuntimeException("Package name ends with . or /: " + this.pkg + " + " + this.simple);
		}
		if (this.simple == null) {
			throw new RuntimeException("Null simple name!");
		}
		if (this.simple.startsWith(".") || this.simple.startsWith("$")) {
			throw new RuntimeException("Simple name starts with . or $: " + this.pkg + " + " + this.simple);
		}
	}
	
	/**
	 * @return The package component of the name as a JVM internal format String.
	 */
	public String getPackage() {
		return pkg == null ? "" : pkg;
	}
	
	/**
	 * @return The package component of the name as a Java source format String.
	 */
	public String getSourcePackage() {
		return pkg == null ? "" : pkg.replace('/', '.');
	}
	
	/**
	 * @return The simple name component of the name as a JVM internal format String.
	 */
	public String getSimpleName() {
		return simple;
	}
	
	/**
	 * @return The simple name component of the name as a Java source format String.
	 */
	public String getSourceSimpleName() {
		return simple.replace('$', '.').replace('/', '.');
	}
	
	/**
	 * @return The source format String for the full name.
	 */
	public String toSourceString() {
		return pkg == null ? simple.replace('$', '.').replace('/', '.') : pkg.replace('/', '.') + '.' + simple.replace('$', '.').replace('/', '.');
	}
	
	/**
	 * @return The internal format String for the full name.
	 */
	public String toInternalString() {
		return pkg == null ? simple : pkg + '/' + simple;
	}
	
	public ObjectTypeDescriptor getType() {
		if (type == null) {
			type = new ObjectTypeDescriptor(this);
		}
		return type;
	}
	
	/******************************************************/
	
	private static final Map<TypeElement,CanonicalName> elementToName = new HashMap<TypeElement,CanonicalName>();
	
	public static CanonicalName of(final TypeElement type, final Elements util) {
//		System.out.println("CanonicalName.of");
//		new Exception().printStackTrace();
		if (elementToName.containsKey(type)) {
			return elementToName.get(type);
		} else {
			final CanonicalName name = new CanonicalName(type, util);
			elementToName.put(type, name);
//			System.out.println("New CanonicalName " + name.toInternalString());
			return name;
		}
	}
	
	private static final Map<String,CanonicalName> fullToName = new HashMap<String,CanonicalName>();
	
	public static CanonicalName of(final String pkg, final String simple) {
		return CanonicalName.of(pkg + '/' + simple);
	}
	
	public static CanonicalName of(final String fullName) {
//		System.out.println("CanonicalName.of");
//		new Exception().printStackTrace();
		if (fullToName.containsKey(fullName)) {
			return fullToName.get(fullName);
		} else {
			final CanonicalName name = new CanonicalName(fullName);
			fullToName.put(fullName, name);
//			System.out.println("New CanonicalName " + name.toInternalString());
			return name;
		}
	}

}
