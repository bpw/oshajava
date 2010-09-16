package test.run.bb;
// from Cody
import oshajava.annotation.*;

@Group(id="BB")
@Module("test.run.bb.BoundedBuffer")
@InterfaceGroup(id="INTER")
public class BoundedBuffer {
	private Node front;
	private Node back;
	private int size;
	private int bound;

	// Create an empty BoundedBuffer with enough space to fit bound
	// number of E objects.
//	@Writer({ "BB" })
	@Writer({ "BB" })
	public BoundedBuffer(int bound) {
		front = null;
		back = null;
		size = 0;
		this.bound = bound;
	}
	

	// Wait for a state where an element can be added to the
	// BoundedBuffer and then do it.
//	@Reader({ "BB" })
//	@Writer({ "INTER", "BB" })
	@Reader({ "BB" })
	@Writer({ "INTER", "BB" })
	public synchronized void enqueue(int x) {
		while (size == bound) { // Full buffer.
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		notifyAll();
		front = new Node(x,front);
		if (back == null) {
			back = front;
		}
		size++;
	}

	// Wait for a state where an element can be removed from the
	// BoundedBuffer and then return the subsequently removed element.
//	@Writer({ "BB" })
//	@Reader({ "INTER", "BB" })
	@Writer({ "BB" })
	@Reader({ "INTER", "BB" })
	public synchronized int dequeue() {
		while (front == null) { // Empty buffer.
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		notifyAll();
		int el = front.data;
		if (front == back) {
			back = null;
		}
		front = front.next;
		size--;
		return el;
	}

	// Simple Node class for internal linked list structure.
	@Inline
	private class Node {
		public int data;
		public Node next;

		public Node(int data, Node next) {
			this.data = data;
			this.next = next;
		}
	}
}
