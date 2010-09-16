package test.bytecode;

public class Array {

	void foo() {
		int[][][][] foo = new int[2][][][];
		foo[1] = new int[2][3][2];
		foo[1][1][1] = new int[10];
	}
}
