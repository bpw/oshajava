package test.bytecode;

public class Constructors {
	int x;
	public Constructors() {
		this(0);
	}
	public Constructors(int i) {
		f();		
	}
	public void f() {
		x = 2;
	}
}

class C extends Constructors {
	int y;
	public void f() {
		super.f();
		y = 2;
	}
}