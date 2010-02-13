package oshajava.runtime;

import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;
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
	
	/**
	 * Set of IDs of writer stacks that this stack is allowed to read from.
	 * 
	 * NOTE: this swaps the old readerset approach for the following reason:
	 * If we load a writerCache in a method and many communications happen, we
	 * have it in the (actual hardware) cache.  If we load the readerset from
	 * a state representing a write, chances are it's not in the cache...
	 */
	protected final BitVectorIntSet writerCache = new BitVectorIntSet();
	
	private Stack(final int method, final Stack parent) {
		this.method = method;
		this.parent = parent;
		
		if (COUNT_STACKS) stacksCreated.inc();
	}
	
	/**
	 * Generate an ID for this stack.
	 */
	public int generateID() {
		synchronized(Stack.class) {
			if(id == Integer.MAX_VALUE) {
				id = ++idCounter;
			}
		}
		return id;
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
	 * Check if two stacks are equal. Due to hashconsing. Two stacks are equal if they have the
	 * same top method and their parents are pointer-equal.
	 */
	@Override
	public boolean equals(Object otherObject) {
		try {
			final Stack other = (Stack)otherObject;
			return method == other.method && (parent == null || parent.equals(other.parent));
		} catch (ClassCastException e) {
			return false;
		}
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
