package oshajava.runtime;

public interface StackCommMonitor {

	public static final StackCommMonitor def = new TextPrinter(System.out);
	
	public void addCommunicationFlush(final Stack s1, final Stack s2);
	
	public void addCommunication(final Stack s1, final Stack s2);
	
	public void flushComms();
}
