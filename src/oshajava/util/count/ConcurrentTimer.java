package oshajava.util.count;

public class ConcurrentTimer extends AbstractCounter<Long> {

	private long elapsed = 0;
	private ThreadLocal<Long> lastStart = new ThreadLocal<Long>();
	public ConcurrentTimer(String desc) {
		super(desc);
	}
	
	public void start() {
		lastStart.set(System.currentTimeMillis());
	}
	
	public void stop() {
		long end = System.currentTimeMillis();
		Long start = lastStart.get();
		if (start == null) {
			throw new RuntimeException("bad timer scope: repeated stops before start");
		}
		synchronized(this) {
			elapsed += end - start;
		}
		lastStart.set(null);
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
