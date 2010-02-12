import java.io.IOException;


public class Except {
	
	int z;
	
	void foo(int x) {
		if ( x > 1) throw new RuntimeException();
		else {
			x = 0;
		}
	}
	
	void bar(int x) {
		foo(x);
	}
	
	void qux(boolean b) {
		try {
			if (b) {
				bar(1);
			} else {
//				throw new oshaj.runtime.IllegalSharingException(Thread.currentThread(), "", Thread.currentThread(), "");
			}
		} catch (oshajava.runtime.IllegalCommunicationException e) {
			System.out.println("ice");
			throw e;
		} catch (RuntimeException t) {
			throw t;
		}
	}
	
	void e() {
		try {
			z = 9;
		} catch (oshajava.runtime.IllegalCommunicationException e) {
			
		}
	}
	
	public static void main(String[] args){
		new Except().bar(10);
	}
	
	void thrower() throws IOException {}
}
