package test.bytecode;

public class Super {
	int x;
	public Super(int i) {
		
	}

}

class Sub extends Super {
	public Sub() {
		super(1);
	}
	void s() {
		x++;
	}
}

class Other {
	void f(Sub s) {
		s.x++;
	}
}