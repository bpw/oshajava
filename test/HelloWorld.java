import oshajava.annotation.*;

public class HelloWorld {
	@NonComm
	public static synchronized final void main(String[] args) {
		System.out.println("Hello world!");
	}
}
