package oshajava.spec;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;


public class MethodSpec implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Kind { INLINE, NONCOMM, COMM, ERROR };
	private final Set<Group> readGroups, writeGroups;
	private final Kind kind;
	public MethodSpec(Kind kind) {
		this(kind, null, null);
	}
	public MethodSpec(Kind kind, Set<Group> readGroups, Set<Group> writeGroups) {
		this.kind = kind;
		this.readGroups = readGroups;
		this.writeGroups = writeGroups;
	}
	
	public Kind kind() { return kind; }
	public Set<Group> readGroups() { return readGroups  == null ? null : Collections.unmodifiableSet(readGroups); }
	public Set<Group> writeGroups() { return writeGroups  == null ? null : Collections.unmodifiableSet(writeGroups); }
	
	public void removeGroup(Group g) {
		if (readGroups != null) readGroups.remove(g);
		if (writeGroups != null) writeGroups.remove(g);
	}
	
	public static final MethodSpec INLINE = new MethodSpec(Kind.INLINE), NONCOMM = new MethodSpec(Kind.NONCOMM), 
		ERROR = new MethodSpec(Kind.ERROR);
	public static MethodSpec DEFAULT = NONCOMM;
	
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

