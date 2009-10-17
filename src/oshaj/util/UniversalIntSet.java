package oshaj.util;

public class UniversalIntSet extends IntSet {
	
	public static final UniversalIntSet set = new UniversalIntSet();
	
	private UniversalIntSet() {}
	
	public void add(int i) {}
	public boolean contains(int i) { return true; }
}
