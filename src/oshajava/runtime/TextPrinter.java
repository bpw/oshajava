package oshajava.runtime;

import java.util.LinkedList;
import java.util.Queue;

import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;

public class TextPrinter implements StackCommMonitor {

	public static final TextPrinter printer = new TextPrinter();
	
	private final Queue<String> queue = new LinkedList<String>();
	
	public TextPrinter() {}
	
	public void addCommunicationFlush(final Stack s1, final Stack s2) {
		addCommunication(s1, s2);
		flushComms();
	}
	
	public void addCommunication(final Stack s1, final Stack s2) {
		int modId = Spec.getModuleID(s1.methodUID);
		if (modId != Spec.getModuleID(s2.methodUID)) {
			return;
		}
		
		final ModuleSpec spec = Spec.getModule(s1.methodUID);
		final String writerSig = spec.getMethodSignature(s1.methodUID);
		Stack pops = null;
		Stack reader = s2;
		do {
			String comm = writerSig + " -> " +
					spec.getMethodSignature(reader.methodUID) + "\t\t";
			comm += spec.isAllowed(s1.methodUID, reader.methodUID) ? "ALLOWED" : "BADNESS";
			synchronized (queue) {
				queue.add(comm);	
			}
			
			pops = reader;
			reader = Stack.pop(reader);
		} while (Spec.getModuleID(reader.methodUID) == modId);
		
		final Stack writer = Stack.pop(s1);
		int newModId = Spec.getModuleID(writer.methodUID);
		if (newModId == modId) {
			addCommunication(writer, s2);
		} else if (spec.isPublic(s1.methodUID, pops.methodUID)) {
			addCommunication(writer, reader);
		}
	}
	
	public void flushComms() {
		synchronized (queue) {
			while (!queue.isEmpty()) {
				System.out.println(queue.remove());
			}	
		}
	}
}
