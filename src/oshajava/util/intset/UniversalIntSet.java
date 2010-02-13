package oshajava.util.intset;


public class UniversalIntSet extends IntSet {
	
	public static final UniversalIntSet set = new UniversalIntSet();
	
	private UniversalIntSet() {}
	
	public boolean contains(int i) { return true; }
	
	public String toString() {
		return "UniversalIntSet";
	}
	
	public boolean isEmpty() {
		return false;
	}
}
