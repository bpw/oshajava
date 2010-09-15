package oshajava.spec;

import java.io.Serializable;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

// TODO subclass with MethodName or something?  Unify descriptor handling...
public class CanonicalName implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final String pkg, simple;
	
	public CanonicalName(final String pkg, String simple) {
		this.pkg = pkg == null || pkg.isEmpty() ? null : pkg.replace('.', '/');
		this.simple = simple.replace('.', '$');
		verify();
	}
	public CanonicalName(final TypeElement type, final Elements util) {
		String qn = type.getQualifiedName().toString();
		this.pkg = util.getPackageOf(type).getQualifiedName().toString().replace('.', '/');
		this.simple = qn.substring(pkg.length() + 1).replace('.', '$');
		verify();
	}
	
	public CanonicalName(String qualifiedName) {
		qualifiedName = qualifiedName.replace('.', '/');
		int i = qualifiedName.lastIndexOf('/');
		this.pkg = i == -1 ? null : qualifiedName.substring(0, i);
		this.simple = qualifiedName.substring(i + 1).replace('.', '$');
		verify();
	}
	
	private void verify() {
		if (pkg != null && (pkg.endsWith(".") || this.pkg.endsWith("/"))) throw new RuntimeException("Package name ends with . or /: " + this.pkg + " + " + this.simple);
		if (this.simple == null) throw new RuntimeException("Null simple name!");
		if (this.simple.startsWith(".") || this.simple.startsWith("$")) throw new RuntimeException("Simple name starts with . or $: " + this.pkg + " + " + this.simple);
	}
	
	public String getPackage() {
		return pkg == null ? "" : pkg;
	}
	
	public String getSourcePackage() {
		return pkg == null ? "" : pkg.replace('/', '.');
	}
	
	public String getSimpleName() {
		return simple;
	}
	
	public String getSourceSimpleName() {
		return simple.replace('$', '.').replace('/', '.');
	}
	
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		return toString().equals(other.toString());
	}
	
	@Override
	public String toString() {
		return toSourceString();
	}
	
	public String toSourceString() {
		return pkg == null ? simple.replace('$', '.').replace('/', '.') : pkg.replace('/', '.') + '.' + simple.replace('$', '.').replace('/', '.');
	}
	
	public String toInternalString() {
		return pkg == null ? simple : pkg + '/' + simple;
	}
}
