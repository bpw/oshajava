package test.run.enclose;
import oshajava.annotation.Module;

@Module("test.run.enclose.Z")
public class Enclose {

	@Module("test.run.enclose.A")
	void a(final int i) {
		@Module("test.run.enclose.A")
		class A {
			int aa() {
				return i;
			}
		}
	}
	
	@Module("test.run.enclose.B")
	void b() {
		
		Object o = new Object() {
			@Module("test.run.enclose.B")
			void bb() {
				
			}
		};
	}
	
	static class C {
		
	}
	
	public static void main(String[] args) {
		new Enclose().a(7);
		new Enclose().b();
		C c = new C();
	}
}
