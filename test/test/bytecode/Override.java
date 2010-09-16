package test.bytecode;

public class Override {
	public Override() {
		foo();
	}
	protected void foo() {};
}

class Over extends Override {
	protected void foo() {
		super.foo();
	}
}