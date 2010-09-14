package oshajava.spec;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import oshajava.support.acme.util.Util;

public 	class Group implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@SuppressWarnings("serial")
	static class DuplicateMethodException extends Exception {
		enum Kind { READER, WRITER };
		protected final Group group;
		protected final String sig;
		protected final Kind kind;
		public DuplicateMethodException(Group group, String sig, Kind kind) {
			super("Method " + sig + " is already entered in group " + group + ".");
			this.group = group;
			this.sig = sig;
			this.kind = kind;
		}
		public String getMethod() {
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
	private final Set<String> readers = new HashSet<String>(), writers = new HashSet<String>();
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
	public Iterable<String> readers() {
		return readers;
	}
	public Iterable<String> writers() {
		return writers;
	}
	public void addReader(String sig) throws DuplicateMethodException {
		Util.assertTrue(sig != null, "Null method sig");
		if (readers.contains(sig)) {
			throw new DuplicateMethodException(this, sig, DuplicateMethodException.Kind.READER);
		}
		readers.add(sig);
	}
	public void addWriter(String sig) throws DuplicateMethodException {
		Util.assertTrue(sig != null, "Null method sig");
		if (writers.contains(sig)) {
			throw new DuplicateMethodException(this, sig, DuplicateMethodException.Kind.WRITER);
		}
		writers.add(sig);
	}
	public String toString() {
		return getName();
	}
}
