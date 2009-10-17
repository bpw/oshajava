
public class Initializers {
	protected Object obj = new Object();
	
	public Initializers(int x) {
		int j = 1;
	}

	protected Object ob = new Object();
	
	protected synchronized void foo() {
		obj.toString();
		new Initializers(0);
	}
	
	static {
		System.out.println("static init");
	}
}
