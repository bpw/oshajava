package oshajava.spec;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import oshajava.spec.names.MethodDescriptor;
import oshajava.support.acme.util.Assert;

public 	class Group implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@SuppressWarnings("serial")
	static class DuplicateMethodException extends Exception {
		enum Kind { READER, WRITER };
		protected final Group group;
		protected final MethodDescriptor sig;
		protected final Kind kind;
		public DuplicateMethodException(Group group, MethodDescriptor sig, Kind kind) {
			super("Method " + sig + " is already entered in group " + group + ".");
			this.group = group;
			this.sig = sig;
			this.kind = kind;
		}
		public MethodDescriptor getMethod() {
			return sig;
		}
		public Group getGroup() {
			return group;
		}
		public Kind getKind() {
			return kind;
		}
	}
	
	enum Kind { COMM, INTERFACE };

	private final Kind kind;
	private final Set<MethodDescriptor> readers = new HashSet<MethodDescriptor>(), writers = new HashSet<MethodDescriptor>();
	private final String name;
	
	public Group(Kind kind, String name) {
		this.kind = kind;
		this.name = name;
	}
	
	public Kind kind() {
		return kind;
	}
	
	public String getName() {
		return name;
	}
	
	public Iterable<MethodDescriptor> readers() {
		return readers;
	}
	
	public Iterable<MethodDescriptor> writers() {
		return writers;
	}
	
	public void addReader(MethodDescriptor sig) throws DuplicateMethodException {
		Assert.assertTrue(sig != null, "Null method sig");
		if (readers.contains(sig)) {
			throw new DuplicateMethodException(this, sig, DuplicateMethodException.Kind.READER);
		}
		readers.add(sig);
	}
	
	public void addWriter(MethodDescriptor sig) throws DuplicateMethodException {
		Assert.assertTrue(sig != null, "Null method sig");
		if (writers.contains(sig)) {
			throw new DuplicateMethodException(this, sig, DuplicateMethodException.Kind.WRITER);
		}
		writers.add(sig);
	}
	
	public void removeMethod(String sig) {
		readers.remove(sig);
		writers.remove(sig);
	}
	
	public boolean isEmpty() {
		return readers.isEmpty() && writers.isEmpty();
	}
	
	public String toString() {
		return getName();
	}
}
