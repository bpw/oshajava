
public class Override {
	public Override() {
		foo();
	}
	void foo() {};
}

class Over extends Override {
	void foo() {
		super.foo();
	}
}