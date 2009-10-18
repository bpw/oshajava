
public class Finally {
	
	Object foo;
	
	public static void main(String[] args) {
		int x =  0;
		try {
			x = x / x;
		} catch (RuntimeException t) {
			x = 1;
			throw t;
		}
	}
	
	void bar() {
		Object a = new Runnable() {
			Finally this$0;
			public void run() {
				foo = new Object();
			}
			
		};
	}

}
