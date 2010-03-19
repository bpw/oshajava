package oshajava.util.count;

public class MaxRecorder extends AbstractCounter<Integer> {
	private int max;
	
	public MaxRecorder(final String desc, final int max) {
		super(desc);
		this.max = max;
	}
	public MaxRecorder(final String desc) {
		this(desc, Integer.MIN_VALUE);
	}
	
	public synchronized void add(final int x) {
		if (x > max) {
			max = x;
		}
	}
	
	public synchronized Integer value() {
		return max;
	}
	
	public synchronized String valueToPy() {
		return max + "";
	}
}
