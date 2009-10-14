
public class Locking {
	int i = 0;
	final int j;
	Object lock = new Object();
	
	public Locking(int a) {
		j = a;
	}
	
	public Locking() {
		this(9);
	}
	public void foo() {
		synchronized(lock) {
			i++;
		}
	}
	
	public static void main(String[] args) {
		Locking l = new Locking(3);
		l.i = 9;
		l.foo();
	}
}
