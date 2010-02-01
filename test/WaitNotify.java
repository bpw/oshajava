import oshajava.annotation.*;

public class WaitNotify {
	static Object LOCK = new Object();
	static int iterations = 0;
	
	public static void main(String[] argv) {
		for (int i=0; i<10; i++) {
		    MyThread t = new MyThread();
		    t.start();
		}
		
		boolean done = false;
		while (!done) {
    		synchronized(LOCK) {
    		    if (iterations >= 10*50) {
    		        done = true;
    		    } else {
            		LOCK.notifyAll();
            	}
            	try {
            	    Thread.sleep(1);
            	} catch (InterruptedException e) {
            	    System.out.println("interrupted!");
            	}
        	}
    	}
	}
}

class MyThread extends Thread {    
    @ReadByAll
    public void run() {
        for (int i=0; i<50; i++) {
            
            Thread thread = Thread.currentThread();
            try {
                synchronized(WaitNotify.LOCK) {
                    Thread.sleep(4);
                    WaitNotify.LOCK.wait();
                    Thread.sleep(4);
                    WaitNotify.iterations++;
                    System.out.println(WaitNotify.iterations);
                }    
                Thread.sleep(10);
            } catch (InterruptedException e) {
                System.out.println("interrupted!");
            }
            
            
        }
    }
}
