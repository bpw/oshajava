package test.compile;
import oshajava.annotation.*;

public class InnerClassTest {

    public static void main(String[] args) {
	new InnerClassTest();
    }

    public InnerClassTest() {
	InnerClass ic = new InnerClass(1,2,3);
	ic.start();
	try {
	    ic.join();
	} catch (InterruptedException e) {}
	System.out.println(ic.getX());;
    }

    private class InnerClass extends Thread {
	private int x,y,z;

	public InnerClass(int x, int y, int z) {
	    this.x = x; this.y = y; this.z = z;
	}

	public int getX() {
	    return x;
	}

	public void run() {
	    x++;
	    y++;
	    z++;
	}
    }
}
