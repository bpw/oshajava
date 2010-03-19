package oshajava.util.count;

public class SequentialTimer extends AbstractCounter<Long> {

	private static final long OFF = -1;
	private long elapsed = 0;
	private long lastStart = OFF;
	public SequentialTimer(String desc) {
		super(desc);
	}
	
	public synchronized void start() {
		assert lastStart == OFF;
		lastStart = System.currentTimeMillis();
	}
	
	public synchronized void stop() {
		long end = System.currentTimeMillis();
		assert lastStart != OFF;
		elapsed += end - lastStart;
		lastStart = OFF;
	}

	@Override
	public Long value() {
		return elapsed;
	}

	@Override
	public String valueToPy() {
		return elapsed + "";
	}

}
