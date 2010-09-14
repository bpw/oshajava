package oshajava.sourceinfo;


public class MethodSpec {
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
}

