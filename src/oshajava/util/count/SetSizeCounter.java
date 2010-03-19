package oshajava.util.count;

import java.util.HashSet;

import oshajava.util.Py;

public class SetSizeCounter<T> extends AbstractCounter<Integer> {
	private final HashSet<T> set = new HashSet<T>();
	public SetSizeCounter(String desc) {
		super(desc);
	}
	public void add(T t) {
		set.add(t);
	}
	public Integer value() {
		return set.size();
	}
	
	public String valueToPy() {
		return set.size() + "";
	}
}
