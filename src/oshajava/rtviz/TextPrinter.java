package oshajava.rtviz;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import oshajava.spec.CompiledModuleSpec;
import oshajava.spec.ModuleSpec;

public class TextPrinter extends StackEdgeGatherer {

	private final Queue<String> queue = new LinkedList<String>();
	private final PrintStream out;

	public TextPrinter(PrintStream output) {
		out = output;
	}

	public void handleEdge(ModuleSpec module, int writerID, int readerID) {
		String comm = module.getMethodSignature(writerID) + " -> " +
				module.getMethodSignature(readerID) + "\t\t" +
				(module.isAllowed(writerID, readerID) ? "ALLOWED" : "BADNESS");
		synchronized (queue) {
			queue.add(comm);	
		}
	}

	public void flushComms() {
		synchronized (queue) {
			while (!queue.isEmpty()) {
				out.println(queue.remove());
			}	
		}
	}
}
