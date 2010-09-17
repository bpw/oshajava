package oshajava.spec;

import java.lang.instrument.IllegalClassFormatException;

import oshajava.spec.names.CanonicalName;

/**
 * To be thrown when a module map could not be found at runtime.
 * @author bpw
 *
 */
@SuppressWarnings("serial")
public class ModuleMapNotFoundException extends IllegalClassFormatException {
	public ModuleMapNotFoundException(CanonicalName name) {
		super(name.toSourceString());
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

