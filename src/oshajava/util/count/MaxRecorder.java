package oshajava.util.count;

public class MaxRecorder {
	private long max;
	
	public MaxRecorder(final long max) {
		this.max = max;
	}
	public MaxRecorder() {
		this(Long.MIN_VALUE);
	}
	
	public synchronized void add(final long x) {
		if (x > max) {
			max = x;
		}
	}
	
	public synchronized long value() {
		return max;
	}
}
