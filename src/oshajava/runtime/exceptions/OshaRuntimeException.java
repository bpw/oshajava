package oshajava.runtime.exceptions;

import oshajava.runtime.Config;
import oshajava.runtime.RuntimeMonitor;

public abstract class OshaRuntimeException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2528906495574526086L;
	
	private static final String CANONICAL_NAME = RuntimeMonitor.class.getCanonicalName();
		
	public OshaRuntimeException() {
		if (Config.fudgeExceptionTracesOption.get()) {
			// fudge the stack to remove the top frame(s?) belonging to the RuntimeMonitor and make it
			// look like the VM threw the exception inside whatever user code method caused it.
			StackTraceElement[] stack = getStackTrace();
			int i = 0;
			while (i < stack.length && stack[i].getClassName().equals(CANONICAL_NAME)) {
				i++;
			}
			if (i > 0) {
				StackTraceElement[] fudgedStack = new StackTraceElement[stack.length - i];
				System.arraycopy(stack, i, fudgedStack, 0, stack.length - i);
				setStackTrace(fudgedStack);
			}
		}
	}
}
