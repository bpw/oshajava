/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.util;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;



public class Py {

	public static String tuple(Object... elems) {
		String s = "(";
		boolean first = true;
		for (Object t : elems) {
			if (!first) s += (", ");
			s += (t.toString());
			first = false;
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
			first = false;
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
			first = false;
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
