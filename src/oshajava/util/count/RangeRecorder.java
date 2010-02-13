package oshajava.util.count;

public class RangeRecorder {
	private long min;
	private long max;
	
	public RangeRecorder(final long initMin, final long initMax) {
		min = initMin;
		max = initMax;
	}
	public RangeRecorder() {
		this(Long.MAX_VALUE, Long.MIN_VALUE);
	}
	
	public synchronized void add(final long x) {
		if (x < min) {
			min = x;
		}
		if (x > max) {
			max = x;
		}
	}
	
	public static class Range {
		public final long min, max;
		public Range(final long min, final long max) {
			this.min = min;
			this.max = max;
		}
	}
	
	public synchronized Range value() {
		return new Range(min, max);
	}
}
