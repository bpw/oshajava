package oshajava.util.count;

public class RangeRecorder extends AbstractCounter<RangeRecorder.Range> {
	private int min;
	private int max;
	private Range range = new Range();
	
	public RangeRecorder(final String desc, final int initMin, final int initMax) {
		super(desc);
		min = initMin;
		max = initMax;
	}
	public RangeRecorder(final String desc) {
		this(desc, Integer.MAX_VALUE, Integer.MIN_VALUE);
	}
	
	public synchronized void add(final int x) {
		if (x < min) {
			min = x;
		}
		if (x > max) {
			max = x;
		}
	}
	
	class Range {
		public String toString() {
			return "min: " + min + ", max: " + max;
		}
	}
	
	public Range value() {
		return range;
	}
	
	public int getMin() {
		return min;
	}
	public int getMax() {
		return max;
	}
}
