package oshajava.util.count;

import java.util.Vector;

import oshajava.support.acme.util.Util;

public abstract class AbstractCounter<T> {
	
	private static final Vector<AbstractCounter<?>> counts = new Vector<AbstractCounter<?>>();
	
	public static void printCounts() {
		for (AbstractCounter<?> c : counts) {
			Util.log(c);
		}
	}

	private final String desc;
	public AbstractCounter(final String desc) {
		this.desc = desc;
		counts.add(this); // escape. watch out.
	}
	
	public abstract T value();
	
	public String toString() {
		return desc + ": " + value();
	}
}
