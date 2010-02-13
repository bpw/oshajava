package oshajava.util.count;

public class Counter {
	private long count;
	
	public Counter(final long init) {
		count = init;
	}
	public Counter() {
		this(0);
	}
	
	public synchronized long inc() {
		return ++count;
	}
	
	public synchronized long dec() {
		return --count;
	}
	
	public synchronized long value() {
		return count;
	}
}
