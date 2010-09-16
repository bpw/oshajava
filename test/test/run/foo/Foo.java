package test.run.foo;

import oshajava.annotation.Group;
import oshajava.annotation.Reader;
import oshajava.annotation.Writer;

@Group(id="A")
public class Foo {
	int a;
	@Reader("A") @Writer("A")
	public void foo() {
		a = 1;
	}
	
	@Group(id="A")
	public static void main(String[] args) {
		System.out.println("test.run.foo");
		new Foo().foo();
	}
}
