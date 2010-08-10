package oshajava.runtime;

public interface StackCommMonitor {

	public static final boolean TRACE = true;
	public static final StackCommMonitor def  = new VisualGrapher(); //new TextPrinter(System.out);
	
	public void addCommunicationAndFlush(final Stack s1, final Stack s2);
	
	public void addCommunication(final Stack s1, final Stack s2);
	
	public void flushComms();
}
