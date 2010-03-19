package oshajava.util.count;

public class Counter extends AbstractCounter<Long> {
	private long count;
	
	public Counter(final String desc, final long init) {
		super(desc);
		count = init;
	}
	public Counter(final String desc) {
		this(desc, 0);
	}
	
	public synchronized long inc() {
		return ++count;
	}
	
	public synchronized long dec() {
		return --count;
	}
	
	public synchronized void add(long l) {
		count += l;
	}
	
	public synchronized Long value() {
		return count;
	}
	
	public synchronized String valueToPy() {
		return count + "";
	}
}
