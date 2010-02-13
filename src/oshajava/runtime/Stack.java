package oshajava.runtime;

import java.util.IdentityHashMap;

import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;
import oshajava.support.acme.util.identityhash.IdentityHashSet;
import oshajava.util.count.Counter;
import oshajava.util.intset.BitVectorIntSet;

public class Stack {
	
	public static final Counter stacksCreated = new Counter();
	public static final boolean COUNT_STACKS = true;

	/**
	 * The Stack for the caller of the top of this stack.
	 */
	public final Stack parent;
	
	/**
	 * The top method on this call stack.
	 */
	public final int method;
	
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
	protected final BitVectorIntSet writerCache = new BitVectorIntSet();
	
	private final IdentityHashSet<Stack> writerMemoTable = new IdentityHashSet<Stack>();
	
	private Stack(final int method, final Stack parent) {
		this.method = method;
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
	 * Get the stack formed by pushing the given callee on the given stack.
	 * @param m
	 * @param stack
	 * @return
	 */
	public static Stack push(final int m, final Stack stack) {
		return get(m,stack);
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
			// FIXME
			final boolean success = true;
			if (success) {
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
	
	/**
	 * Check if two stacks are equal. pointer equality.
	 */
	@Override
	public boolean equals(Object otherObject) {
		return this == otherObject;
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
	 * Get the canonical Stack for a given method and parent.
	 * @param method
	 * @param parent
	 * @return
	 */
	// TODO Cache recent ones in each thread.
	private static Stack get(final int method, final Stack parent) {
		// TODO? if (parent.method == method) return parent;
		ConcurrentIdentityHashMap<Stack,Stack>  t = hashConsTable.get(method);
		if (t == null) {
			t = new ConcurrentIdentityHashMap<Stack,Stack>();
			final ConcurrentIdentityHashMap<Stack,Stack> s = hashConsTable.putIfAbsent(method, t);
			if (s != null) t = s;
		}
		Stack stack = t.get(parent);
		if (stack == null) {
			stack = new Stack(method,parent);
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
	 * Get the last id issued for a stack.
	 * @return
	 */
	public static synchronized int lastID() {
		return idCounter;
	}
}
