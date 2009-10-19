package oshaj.runtime;



public abstract class IllegalCommunicationException extends RuntimeException {

	/**
	 * Auto-generated version ID.
	 */
	private static final long serialVersionUID = -8360898879626150853L;
	
	protected final String writerMethod, readerMethod;
	protected final ThreadState writerThread, readerThread;
	
	protected IllegalCommunicationException(ThreadState writerThread, String writerMethod, 
			ThreadState readerThread, String readerMethod) {
		this.writerMethod = writerMethod;
		this.readerMethod = readerMethod;
		this.writerThread = writerThread;
		this.readerThread = readerThread;
		StackTraceElement[] stack = getStackTrace();
		int i = 0;
		while (i < stack.length && stack[i].getClass().equals(oshaj.runtime.RuntimeMonitor.class)) i++;
		if (i > 0) {
			StackTraceElement[] fudgedStack = new StackTraceElement[stack.length - i];
			System.arraycopy(stack, i, fudgedStack, 0, stack.length - i);
			setStackTrace(fudgedStack);
		}
	}
	
	protected abstract String actionString();

	@Override
	public String getMessage() {
//		String s = "";
//		for (int i = 0; i < MethodRegistry.policyTable.length; i++) {
//			String n = MethodRegistry.lookup(i);
//			if (n != null) {
//				s += n + " (id=" + i + "): " + MethodRegistry.policyTable[i] + "\n";
//			}
//		}
		return String.format(
				"%s in %s %s %s in %s.", // Reader Set was: %s; whole policy table:\n %s", 
				readerThread, readerMethod,
				actionString(),
				writerThread, writerMethod //,
//				MethodRegistry.getPolicy(writerMethod),
//				s
		);
	}

}
