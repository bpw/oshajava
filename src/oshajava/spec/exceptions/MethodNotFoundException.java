package oshajava.spec.exceptions;

import oshajava.spec.Module;

@SuppressWarnings("serial")
public class MethodNotFoundException extends Exception {
	protected final String sig;
	protected final Module module;
	public MethodNotFoundException(String sig, Module module) {
		super("Method " + sig + " not found in module " + module.getName());
		this.sig = sig;
		this.module = module;
	}
}

