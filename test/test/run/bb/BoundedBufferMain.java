package test.run.bb;
// from Cody
import oshajava.annotation.*;

@Group(id="PIPE")
public class BoundedBufferMain {

	@Inline
	public static void main(String[] args) {
		int n = 10;
		BoundedBuffer buf = new BoundedBuffer(10);
		Consumer[] cs = new Consumer[n];
		Producer[] ps = new Producer[n];
		for (int i = 0; i < n; i++) {
			cs[i] = new Consumer(buf);
			ps[i] = new Producer(buf);
			ps[i].start();
		}
		for (int i = 0; i < n; i++) {
			cs[i].start();
		}	
		// Scanner console = new Scanner(System.in);
		// while (true) {
		//     buf.enqueue(console.nextInt());
		// }
	}
}

@Group(id="PR")
class Producer extends Thread {
	private BoundedBuffer buf;

	@Writer("PR")
	public Producer(BoundedBuffer buffer) {
		buf = buffer;
	}

	@Writer("PIPE")
	@Reader("PR")
	public void run() {
		for (int i = 0; i < 10; i++) {
			buf.enqueue(i);
		}
	}
}

@Group(id="CR")
class Consumer extends Thread {
	private static int idCounter = 0;

	private BoundedBuffer buf;
	private int myId;

	@Writer("CR")
	public Consumer(BoundedBuffer buffer) {
		myId = idCounter++;
		buf = buffer;
	}

	@Reader({"CR","PIPE"})
	public void run() {
		System.out.println("Running: " + myId);
		for (int j = 0; j < 10; j++) {
			int i = buf.dequeue();
			System.out.println("ID: " + myId + ",   " + i);
		}
	}
}