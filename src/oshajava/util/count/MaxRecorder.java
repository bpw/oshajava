package oshajava.util.count;

public class MaxRecorder {
	private int max;
	
	public MaxRecorder(final int max) {
		this.max = max;
	}
	public MaxRecorder() {
		this(Integer.MIN_VALUE);
	}
	
	public synchronized void add(final int x) {
		if (x > max) {
			max = x;
		}
	}
	
	public synchronized int value() {
		return max;
	}
}
