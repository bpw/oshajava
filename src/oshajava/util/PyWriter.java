package oshajava.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Stack;

import oshajava.util.count.AbstractCounter;

public class PyWriter {
	private final boolean echo;
	private final Writer py;
	private static enum Construct { LIST, TUPLE, MAP }
	private final Stack<Construct> scope = new Stack<Construct>();
	
	private boolean firstElem = true;
	
	public PyWriter(final String file, final boolean echo) throws FileNotFoundException {
		this.py = new PrintWriter(file);
		this.echo = echo;
	}
	public PyWriter(final Writer py, final boolean echo) {
		this.py = py;
		this.echo = echo;
	}
	
	public void write(String s) throws IOException {
		if (echo) {
			System.err.println(s);
		}
		py.write(s);
	}
	
	public void writeElem(Object o) throws IOException {
		String comma = ", ";
		if (firstElem) {
			firstElem = false;
			comma = "";
		}
		write(comma + o.toString());
	}
	
	public void line() throws IOException {
		write("\n");
	}
	public <T> void writeList(Iterable<T> l) throws IOException {
		startList();
		for (T t : l) {
			writeElem(t);
		}
		endList();
	}
	public <T> void writeTuple(Iterable<T> l) throws IOException {
		startTuple();
		for (T t : l) {
			writeElem(t);
		}
		endTuple();
	}
	public <K,V> void writeMap(Map<K,V> m) throws IOException {
		startMap();
		for (Map.Entry<K,V> e : m.entrySet()) {
			writeElem(e.getKey().toString() + " : " + e.getValue().toString());
		}		
		endMap();
	}
	
	public <T> void writeList(T... args) throws IOException {
		startList();
		for (T t : args) {
			writeElem(t);
		}
		endList();
	}
	public <T> void writeTuple(T... args) throws IOException {
		startTuple();
		for (T t : args) {
			writeElem(t);
		}
		endTuple();
	}
	public void writeMap(Object... args) throws IOException {
		startMap();
		String key = null;
		for (Object o : args) {
			if (key == null) {
				key = o.toString();
			} else {
				writeElem(key + " : " + o.toString());				
				key = null;
			}
		}		
		endMap();
	}
	
	// stream-based
	
	public void startList() throws IOException {
		scope.push(Construct.LIST);
		write("[");
		firstElem = true;
	}
	public void endList() throws IOException {
		if (scope.pop() != Construct.LIST) {
			throw new RuntimeException("Bad py scope.");
		}
		write("]");
		firstElem = false;
	}
	
	public void startTuple() throws IOException {
		scope.push(Construct.TUPLE);
		write("(");
		firstElem = true;
	}
	public void endTuple() throws IOException {
		if (scope.pop() != Construct.TUPLE) {
			throw new RuntimeException("Bad py scope.");
		}
		write(")");
		firstElem = false;
	}
	
	public void startMap() throws IOException {
		scope.push(Construct.MAP);
		write("{");
		firstElem = true;
	}
	public void endMap() throws IOException {
		if (scope.pop() != Construct.MAP) {
			throw new RuntimeException("Bad py scope.");
		}
		write("}");
		firstElem = false;
	}
	
	public void writeMapKey(Object key) throws IOException {
		writeElem(key.toString() + " : ");
	}
	
	public void writeMapKey(String key) throws IOException {
		writeElem(Py.quote(key) + " : ");
	}
	
	public void writeMapPair(Object key, Object val) throws IOException {
		writeElem(key.toString() + " : " +  val.toString());
	}
	
	public void writeMapPair(String key, Object val) throws IOException {
		writeElem(Py.quote(key) + " : " +  val.toString());
	}
	
	
	public void writeCounterAsMapPair(AbstractCounter<?> c) throws IOException {
		writeMapPair(c.getDesc(), c.valueToPy());
	}
	
	public void close() throws IOException {
		py.close();
	}

}

