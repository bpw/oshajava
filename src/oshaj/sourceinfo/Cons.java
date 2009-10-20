package oshaj.sourceinfo;

public class Cons<T> {
	public final T head;
	public final Cons<T> rest;
	
	public Cons(T head, Cons<T> rest) {
		this.head = head;
		this.rest = rest;
	}
}
