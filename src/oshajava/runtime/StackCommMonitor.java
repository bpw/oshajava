package oshajava.runtime;

public interface StackCommMonitor {

	public static final boolean VISUALIZE = Config.visualizeOption.get();
	public static final StackCommMonitor def = VISUALIZE ? new VisualGrapher() : null;
	
	public void addCommunicationAndFlush(final Stack s1, final Stack s2);
	
	public void addCommunication(final Stack s1, final Stack s2);
	
	public void flushComms();
}
