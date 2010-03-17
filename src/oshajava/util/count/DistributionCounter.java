package oshajava.util.count;

import java.util.HashMap;
import java.util.Map;

public class DistributionCounter extends RangeRecorder {
	private final HashMap<Integer,Integer> dist = new HashMap<Integer,Integer>();
	private Distribution distrib = new Distribution();
	
	public DistributionCounter(final String desc) {
		super(desc);
	}
	
	public synchronized void add(int i) {
		super.add(i);
		if(dist.containsKey(i)) {
			dist.put(i, dist.get(i) + 1);
		} else {
			dist.put(i, 1);
		}
	}
	
	public synchronized float amean() {
		int sum = 0, n = 0;
		for (Map.Entry<Integer, Integer> e : dist.entrySet()) {
			sum += e.getKey() * e.getValue();
			n += e.getValue();
		}
		return (float)sum / (float)n;
	}
	
	public HashMap<Integer,Integer> getDist() {
		return dist;
	}
	
	class Distribution extends RangeRecorder.Range {
		public String toString() {
			return String.format("mean: %f, min: %d, max: %d", amean(), getMin(), getMax());
		}
	}
	
	public Distribution value() {
		return distrib;
	}
	
}
