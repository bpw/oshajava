package oshajava.runtime;

import oshajava.sourceinfo.ModuleSpec;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;
import oshajava.support.acme.util.identityhash.IdentityHashSet;
import oshajava.util.count.Counter;
import oshajava.util.count.DistributionCounter;
import oshajava.util.count.SetSizeCounter;
import oshajava.util.intset.BitVectorIntSet;

public class Stack {
	
	public static final Counter stacksCreated = new Counter("Distinct stacks created");
	public static final DistributionCounter communicatingStackDepths = new DistributionCounter("Communicating stack depths");
	public static final DistributionCounter readerStackDepths = new DistributionCounter("Reader stack depths (per comm. pair)");
	public static final DistributionCounter writerStackDepths = new DistributionCounter("Writer stack depths (per comm. pair)");
	public static final DistributionCounter stackDepthDiffs = new DistributionCounter("Writer stack depth - reader stack depth (per comm. pair)");
	public static final DistributionCounter setLengthDist = new DistributionCounter("Length in methods of stack segments");
	public static final DistributionCounter segSizeDist = new DistributionCounter("Set size in methods of stack segments (writers only for now)");
	public static final DistributionCounter segCountDist = new DistributionCounter("Segments on a communicating stack");
	public static final SetSizeCounter<ModuleSpec> modulesUsed = new SetSizeCounter<ModuleSpec>("Modules used");
	
	public static final Counter stackWalks = new Counter("Full stack walks");
	public static final Counter memo2Hits = new Counter("Level 2 memo hits");
	
	public static final boolean COUNT_STACKS = RuntimeMonitor.PROFILE && true;
	
	protected static final Stack root = new Stack(-1, null);
	protected static final Stack classInitializer = new Stack(-1, null);

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
	
	public int getDepth() {
		if (this == root) {
			return 0;
		} else {
			return 1 + parent.getDepth();
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
			if (COUNT_STACKS) {
				memo2Hits.inc();
			}
			yes = writerMemoTable.contains(writer);
		}
		if (!yes) { //  really slow path: full stack traversal
			if (COUNT_STACKS) {
				stackWalks.inc();
			}
			if (walkStacks(writer, this, 0)) {
				synchronized (writerMemoTable) {
					writerMemoTable.add(writer);
				}
				if (COUNT_STACKS) {
					final int wd = writer.getDepth(), rd = getDepth();
					communicatingStackDepths.add(wd);
					communicatingStackDepths.add(rd);
					writerStackDepths.add(wd);
					readerStackDepths.add(rd);
					stackDepthDiffs.add(wd > rd ? wd - rd : rd - wd);
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
	
	private static boolean walkStacks(final Stack writer, final Stack reader, final int segDepth) {
		if (writer == root && reader == root) {
			// Successfully walked to the roots of each stack.
			return true;
		}
		if (writer == classInitializer || reader == classInitializer) {
		    // Allow communication with class initializers.
		    return true;
		}
		if (writer == root && reader != root || writer != root && reader == root) {
			// Layer mismatch at root of stacks.
			// FIXME pop/throw to find a compositional module...
			return false;
		}
		
		final int writerMod = Spec.getModuleID(writer.methodUID);
		if (writerMod == Spec.getModuleID(reader.methodUID)) {
			// Immediate pair are in same module: no compositional module check needed here.
			// Find the reader layer.
			final BitVectorIntSet layer = new BitVectorIntSet();
			final Stack readerLayerTop = reader.expandLayer(layer, writerMod, 0);
			Util.assertTrue(!layer.isEmpty());
			// Find the writer layer and check the layer mapping.
			final ModuleSpec layerModule = Spec.getModule(writer.methodUID);
			modulesUsed.add(layerModule);
			final Stack writerLayerTop;
			try {
				writerLayerTop = writer.checkLayer(layer, layerModule, 0);
			} catch (IllegalInternalEdgeException e) {
				return false;
			}
			// The communication in this layer is allowed.
			// Is the communication exposed?
			if (layerModule.isPublic(writerLayerTop.methodUID, readerLayerTop.methodUID)) {
				// communication is exposed here. Must check rest of stacks.
				return walkStacks(writerLayerTop.parent, readerLayerTop.parent, segDepth + 1);
			} else if (COUNT_STACKS) {
				segCountDist.add(segDepth + 1);
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
	private Stack expandLayer(final BitVectorIntSet layer, final int moduleID, final int depth) {
	    // Should only be called on a stack with at least one method from
	    // the module.
	    Util.assertTrue(moduleID == Spec.getModuleID(methodUID));
	        
        layer.add(Spec.getMethodID(methodUID));
        if (moduleID != Spec.getModuleID(parent.methodUID)) {
        	if (COUNT_STACKS) {
        		setLengthDist.add(depth + 1);
        	}
            // Module boundary.
            return this;
        } else {
            // Continue expanding.
            return parent.expandLayer(layer, moduleID, depth + 1);
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
	private Stack checkLayer(final BitVectorIntSet layer, final ModuleSpec module, int depth) throws IllegalInternalEdgeException {
	    Util.assertTrue(module.getId() == Spec.getModuleID(methodUID));
	    
	    if (COUNT_STACKS) {
	    	segSizeDist.add(layer.size());
	    }
		if (module.allAllowed(methodUID, layer)) {
			if (module.getId() != Spec.getModuleID(parent.methodUID)) {
				if (COUNT_STACKS) {
					setLengthDist.add(depth + 1);
				}
			    // Module boundary.
				return this;
			} else {
			    // Continue checking.
				return parent.checkLayer(layer, module, depth + 1);
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
	    if (this == root) {
	        return "(root)";
	    } else if (this == classInitializer) {
	        return "(class initialization)";
	    }
		final ModuleSpec mod = Spec.getModule(methodUID);
		return " [module: " + mod.getName() + "] " + mod.getMethodSignature(methodUID) + "\n" + (parent != null ? "  called by " + parent.toString() : "");
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
