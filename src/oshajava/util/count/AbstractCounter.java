package oshajava.util.count;

import java.util.Vector;

public abstract class AbstractCounter<T> {
	
	private static final Vector<AbstractCounter<?>> counts = new Vector<AbstractCounter<?>>();
	
	public static Iterable<AbstractCounter<?>> all() {
		return counts;
	}

	private final String desc;
	public AbstractCounter(final String desc) {
		this.desc = desc;
		counts.add(this); // escape. watch out.
	}
	
	public abstract T value();
	public String getDesc() {
		return desc;
	}
	
	public abstract String valueToPy();
	
	public String toString() {
		return desc + ": " + value();
	}
}
