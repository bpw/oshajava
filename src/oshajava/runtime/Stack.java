package oshajava.runtime;

import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;
import oshajava.support.acme.util.identityhash.IdentityHashSet;
import oshajava.util.count.Counter;
import oshajava.util.intset.BitVectorIntSet;

public class Stack {
	
	public static final Counter stacksCreated = new Counter();
	public static final boolean COUNT_STACKS = RuntimeMonitor.PROFILE && true;
	
	protected static final Stack root = new Stack(-1, null);

	/**
	 * The Stack for the caller of the top of this stack.
	 */
	public final Stack parent;
	
	/**
	 * The top method on this call stack.
	 */
	public final int methodUID;
	
	/**
	 * The id of this call stack. Only set for real once this tack has communicated a bit.
	 */
	public int id = Integer.MAX_VALUE;
	
	private static byte ID_THRESHOLD = 8;
	private byte writeCounter = 0;
	
	/**
	 * Set of IDs of writer stacks that this stack is allowed to read from. Used 
	 * in fast path.
	 * 
	 * NOTE: this swaps the old readerset approach for the following reason:
	 * If we load a writerCache in a method and many communications happen, we
	 * have it in the (actual hardware) cache.  If we load the readerset from
	 * a state representing a write, chances are it's not in the cache...
	 */
	public final BitVectorIntSet writerCache = new BitVectorIntSet();
	
	private final IdentityHashSet<Stack> writerMemoTable = new IdentityHashSet<Stack>();
	
	private Stack(final int methodUID, final Stack parent) {
		this.methodUID = methodUID;
		this.parent = parent;
		
		if (COUNT_STACKS) stacksCreated.inc();
	}
	
	/**
	 * Get the caller stack of a stack.
	 * @param stack
	 * @return
	 */
	public static Stack pop(final Stack stack) {
		return stack.parent;
	}
	
	/**
	 * Get the canonical stack formed by pushing the given callee on the given stack.
	 * @param m
	 * @param stack
	 * @return
	 */
	public static Stack push(final int methodUID, final Stack parent) {
		ConcurrentIdentityHashMap<Stack,Stack>  t = hashConsTable.get(methodUID);
		if (t == null) {
			t = new ConcurrentIdentityHashMap<Stack,Stack>();
			final ConcurrentIdentityHashMap<Stack,Stack> s = hashConsTable.putIfAbsent(methodUID, t);
			if (s != null) t = s;
		}
		Stack stack = t.get(parent);
		if (stack == null) {
			stack = new Stack(methodUID,parent);
			final Stack s = t.putIfAbsent(parent, stack);
			if (s == null) {
				return stack;
			} else {
				synchronized(Stack.class) {
					idCounter--;
				}
				return s;
			}
		} else {
			return stack;
		}
	}
	
	/**
	 * Count a write in this stack. If we've passed a threshold,
	 * give this stack an id so reads of its future writes will go
	 * down the fast path.
	 * 
	 * @return the current id
	 */
	private synchronized int countWrite() {
		if (++writeCounter > ID_THRESHOLD) {
			if(id == Integer.MAX_VALUE) {
				synchronized(Stack.class) {
					id = ++idCounter;
				}
			}
		}
		return id;
	}
	
	/**
	 * Check if the spec allows this stack to read from the given writer stack.
	 * 
	 * Slow path.
	 * 
	 * @param writer
	 * @return
	 */
	public boolean checkWriter(Stack writer) {
		final boolean yes;
		synchronized (writerMemoTable) {
			yes = writerMemoTable.contains(writer);
		}
		if (!yes) { //  really slow path: full stack traversal
			if (walkStacks(writer, this)) {
				synchronized (writerMemoTable) {
					writerMemoTable.add(writer);
				}
			} else {
				return false;
			}
		} // else moderately slow path.
		// bump the writer's write count.
		// if it has an id, add it to our cache.
		final int wid = writer.countWrite();
		if (wid != Integer.MAX_VALUE) {
			writerCache.add(wid);
		}
		return true;
	}
	
	private static boolean walkStacks(final Stack writer, final Stack reader) {
		if (writer == root && reader == root) {
			// Successfully walked to the roots of each stack.
			return true;
		}
		if (writer == root && reader != root || writer != root && reader == root) {
			// Layer mismatch at root of stacks.
			// FIXME pop/throw to find a compositional module...
		}
		
		final int writerMod = Spec.getModuleID(writer.methodUID);
		if (writerMod == Spec.getModuleID(reader.methodUID)) {
			// Immediate pair are in same module: no compositional module check needed here.
			// Find the reader layer.
			final BitVectorIntSet layer = new BitVectorIntSet();
			final Stack readerLayerTop = reader.expandLayer(layer, writerMod);
			Util.assertTrue(!layer.isEmpty());
			// Find the writer layer and check the layer mapping.
			final ModuleSpec layerModule = Spec.getModule(writer.methodUID);
			final Stack writerLayerTop;
			try {
				writerLayerTop = writer.checkLayer(layer, layerModule);
			} catch (IllegalInternalEdgeException e) {
				return false;
			}
			// The communication in this layer is allowed.
			// Is the communication exposed?
			if (layerModule.isPublic(writerLayerTop.methodUID, readerLayerTop.methodUID)) {
				// communication is exposed here. Must check rest of stacks.
				return walkStacks(writerLayerTop.parent, readerLayerTop.parent);
			}
			// communication is hidden here. All checks so far succeeded so the
			//communication is valid.
			return true;
		} else {
			// Immediate pair not in same module: do compositional module check.
			// First, see if there's a layer on both stacks that doesn't match up with any caller or callee
			// layer on the other side.
			// Then, try merging with callers.
			// THen try coming back down to merge with callees if needed.
			// The general case is going to have pretty high worst-case theoretical
			// time
			// FIXME
			return false;
		}
	}
	
	/**
	 * Expands the set layer until it includes all methods from the same module
	 * appearing consecutively on the stack.
	 * 
	 * @param layer
	 * @param moduleID
	 * @return the last stack frame that contributed to the set.
	 */
	private Stack expandLayer(final BitVectorIntSet layer, final int moduleID) {
		if (parent == null) {
			return null;
		} else if (moduleID == Spec.getModuleID(parent.methodUID)) {
			layer.add(Spec.getMethodID(parent.methodUID));
			return parent.expandLayer(layer, moduleID);
		} else {
			return this;
		}
	}
	
	/**
	 * Checks that each method in the chunk of consecutive methods from the same module
	 * is allowed to communicate to all of the methods in the opposing chunk.
	 * 
	 * @param layer
	 * @param module
	 * @return the last stack frame on this stack that was in the consecutive chunk
	 * belonging to this module.
	 * @throws IllegalInternalEdgeException if there was an illegal internal edge
	 */
	private Stack checkLayer(final BitVectorIntSet layer, final ModuleSpec module) throws IllegalInternalEdgeException {
		if (parent == null) {
			return null;
		} else if (module.allAllowed(methodUID, layer)) {
			if (module.getId() != Spec.getModuleID(parent.methodUID)) {
				return parent.checkLayer(layer, module);
			} else {
				return this;
			}
		} else {
			throw new IllegalInternalEdgeException();
		}
	}
	
	@SuppressWarnings("serial")
	private static class IllegalInternalEdgeException extends Exception { }
	
	/**
	 * Check if two stacks are equal. pointer equality.
	 */
	@Override
	public boolean equals(Object otherObject) {
		return this == otherObject;
	}
	
	@Override
	public String toString() {
		final ModuleSpec mod = Spec.getModule(methodUID);
		return "[" + mod.getName() + "] " + mod.getMethodSignature(methodUID) + "\n" + (parent != null ? " called by " + parent.toString() : "");
	}
	
	/******************************************************************/
	
	/**
	 * Counter for stack IDs.
	 */
	private static int idCounter = -1;
	
	/**
	 * Hash cons table of all stacks to save memory.
	 */
	// TODO make the whole thing thread-local to avoid synchronization?
	private static final ConcurrentIdentityHashMap<Integer,ConcurrentIdentityHashMap<Stack,Stack>> hashConsTable = 
		new ConcurrentIdentityHashMap<Integer,ConcurrentIdentityHashMap<Stack,Stack>>();

	/**
	 * Get the last id issued for a stack.
	 * @return
	 */
	public static synchronized int lastID() {
		return idCounter;
	}
}
