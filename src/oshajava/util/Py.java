package oshajava.util;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import oshajava.util.intset.BitVectorIntSet;


public class Py {

	public static String tuple(Object... elems) {
		String s = "(";
		boolean first = true;
		for (Object t : elems) {
			if (!first) s += (", ");
			s += (t.toString());
		}
		s += (")");
		return s;
	}
	public static String list(Object... elems) {
		String s = "[";
		boolean first = true;
		for (Object t : elems) {
			if (!first) s += (", ");
			s += (t.toString());
		}
		s += ("]");
		return s;
	}
	public static String map(Object... elems) {
		String s = ("{");
		boolean first = true;
		String key = null;
		for (Object o : elems) {
			if (!first) s += (", ");
			if (key == null) {
				key = o.toString();
			} else {
				s += (key + " : " + o.toString());				
				key = null;
			}
		}		
		s += ("}");
		return s;
	}
	
	public static String quote(Object o) {
		String s = o.toString();
		if ((s.startsWith("\"") || s.startsWith("'")) && (s.endsWith("\"") || s.endsWith("'"))) {
			return s;
		} else {
			return '"' + o.toString() + '"';
		}
	}
	
	public static String repr(String s) {
		return '"' + s + '"';
	}
	
	public static <K,V> String repr(Map<K,V> map) {
		StringWriter s = new StringWriter();
		PyWriter py = new PyWriter(s, false);
		try {
			py.startMap();
			for (Map.Entry<K,V> e : map.entrySet()) {
				py.writeElem(e.getKey().toString() + " : "  + e.getValue().toString());				
			}		
			py.endMap();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return s.toString();
	}
	
	public static <T> String tuple(Iterable<T> c) {
		StringWriter s = new StringWriter();
		PyWriter py = new PyWriter(s, false);
		try {
			py.writeTuple(c);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return s.toString();
	}

	public static <T> String repr(BitVectorIntSet bv) {
		StringWriter s = new StringWriter();
		PyWriter py = new PyWriter(s, false);
		try {
			for (int i = 0; i < bv.size(); i++) {
				if (bv.contains(i)) {
					py.writeElem(i);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return s.toString();
	}

//	public static <T> String tuple(Iterable<T> c) {
//	StringWriter s = new StringWriter();
//	PyWriter py = new PyWriter(s, false);
//	try {
//		py.writeTuple(c);
//	} catch (IOException e) {
//		throw new RuntimeException(e);
//	}
//	return s.toString();
//}

}
