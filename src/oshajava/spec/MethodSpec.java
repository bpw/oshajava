package oshajava.spec;

import java.io.Serializable;


public class MethodSpec implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Kind { INLINE, NONCOMM, COMM, ERROR };
	private final Iterable<Group> readGroups, writeGroups;
	private final Kind kind;
	public MethodSpec(Kind kind) {
		this(kind, null, null);
	}
	public MethodSpec(Kind kind, Iterable<Group> readGroups, Iterable<Group> writeGroups) {
		this.kind = kind;
		this.readGroups = readGroups;
		this.writeGroups = writeGroups;
	}
	
	public Kind kind() { return kind; }
	public Iterable<Group> readGroups() { return readGroups; }
	public Iterable<Group> writeGroups() { return writeGroups; }
	
	public static final MethodSpec INLINE = new MethodSpec(Kind.INLINE), NONCOMM = new MethodSpec(Kind.NONCOMM), 
		DEFAULT = Module.DEFAULT_INLINE ? INLINE : NONCOMM, ERROR = new MethodSpec(Kind.ERROR);
	
	public String toString() {
		switch (kind) {
		case INLINE:
			return "@Inline";
		case NONCOMM:
			return "@NonComm";
		case COMM:
			return (readGroups == null ? "" : "@Reader(" + readGroups + ")") + (writeGroups == null ? "" : "@Writer(" + writeGroups + ")");
		case ERROR:
		default:
			return "Malformed";
		}
	}
}

