package oshajava.spec;

import java.lang.instrument.IllegalClassFormatException;

/**
 * To be thrown when a module spec could not be found at runtime.
 * @author bpw
 *
 */
@SuppressWarnings("serial")
public class ModuleSpecNotFoundException extends IllegalClassFormatException {
	public ModuleSpecNotFoundException(String name) {
		super(name);
	}
	
	public Wrapper wrap() {
		return new Wrapper(this);
	}
	
	public static class Wrapper extends RuntimeException {
		private final ModuleSpecNotFoundException e;
		public Wrapper(ModuleSpecNotFoundException e) {
			this.e = e;
		}
		public ModuleSpecNotFoundException unwrap() {
			return e;
		}
	}
}

