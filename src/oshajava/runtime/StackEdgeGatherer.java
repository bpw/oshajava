package oshajava.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;

public abstract class StackEdgeGatherer implements StackCommMonitor {
	
	private final Map<Stack,Set<Stack>> done = new HashMap<Stack,Set<Stack>>();

	public void addCommunicationAndFlush(final Stack s1, final Stack s2) {
		addCommunication(s1, s2);
		flushComms();
	}
	
	public void addCommunication(final Stack s1, final Stack s2) {
		synchronized (done) {
			if (done.containsKey(s1)) {
				if (done.get(s1).contains(s2)) {
					return; // Already done these comms.
				} else {
					done.get(s1).add(s2);
				}
			} else {
				done.put(s1, new HashSet<Stack>());
				done.get(s1).add(s2);
			}	
		}
		
		int modId = Spec.getModuleID(s1.methodUID);
		if (modId != Spec.getModuleID(s2.methodUID)) {
			return;
		}
		
		final ModuleSpec module = Spec.getModule(s1.methodUID);
		Stack pops = null;
		Stack reader = s2;
		do {
			handleEdge(module, s1.methodUID, reader.methodUID);
			
			pops = reader;
			reader = Stack.pop(reader);
		} while (Spec.getModuleID(reader.methodUID) == modId);
		
		final Stack writer = Stack.pop(s1);
		int newModId = Spec.getModuleID(writer.methodUID);
		if (newModId == modId) {
			addCommunication(writer, s2);
		} else if (module.isPublic(s1.methodUID, pops.methodUID)) {
			addCommunication(writer, reader);
		}
	}
	
	public abstract void handleEdge(ModuleSpec module, int writerID, int readerID);
}
