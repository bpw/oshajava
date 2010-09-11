package oshajava.sourceinfo;

import java.lang.instrument.IllegalClassFormatException;

/**
 * To be thrown when a module map could not be found at runtime.
 * @author bpw
 *
 */
@SuppressWarnings("serial")
public class ModuleMapNotFoundException extends IllegalClassFormatException {
	public ModuleMapNotFoundException(String name) {
		super(name);
	}
	
	public Wrapper wrap() {
		return new Wrapper(this);
	}
	
	public static class Wrapper extends RuntimeException {
		private final ModuleMapNotFoundException e;
		public Wrapper(ModuleMapNotFoundException e) {
			this.e = e;
		}
		public ModuleMapNotFoundException unwrap() {
			return e;
		}
	}
}

