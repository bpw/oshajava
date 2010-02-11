package oshajava.sourceinfo;

/**
 * To be thrown when a module spec could not be found at runtime.
 * @author bpw
 *
 */
public class ModuleSpecNotFoundException extends RuntimeException {
	public ModuleSpecNotFoundException(String name) {
		super(name);
	}
}
