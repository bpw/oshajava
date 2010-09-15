package oshajava.util;

import java.util.ArrayList;
import java.util.List;

import oshajava.instrument.InstrumentationAgent;
import oshajava.spec.CanonicalName;
import oshajava.support.acme.util.Util;

public class Desc {

	public final String cls, method, ret;
	public final List<String> params = new ArrayList<String>();
	
	public Desc(CanonicalName desc) {
		String[] parts = desc.toInternalString().split("\\.");
		this.cls = InstrumentationAgent.sourceName(parts[0]);
		
		int descIndex = parts[1].indexOf('(') + 1;
		this.method = parts[1].substring(0, descIndex - 1);
		
		int retIndex = parts[1].lastIndexOf(')') + 1;
		this.ret = parseTypeDesc(parts[1].substring(retIndex)).first;
		
		String paramString = parts[1].substring(descIndex, retIndex - 1); 
		while (!paramString.isEmpty()) {
			Pair<String,String> p = parseTypeDesc(paramString);
			params.add(p.first);
			paramString = p.second;
		}
	}
	
	static class Pair<X,Y> {
		final X first;
		final Y second;
		public Pair(X x, Y y) {
			first = x;
			second = y;
		}
	}
	
	public static Pair<String,String> parseTypeDesc(String desc) {
		String arrayType = "";
		while (desc.charAt(0) == '[') {
			arrayType += "[]";
			desc = desc.substring(1);
		}
		String type;
		switch (desc.charAt(0)) {
		case 'B':
			type = "byte";
			desc = desc.substring(1);
			break;
		case 'C':
			type = "char";
			desc = desc.substring(1);
			break;
		case 'D':
			type = "double";
			desc = desc.substring(1);
			break;
		case 'F':
			type = "float";
			desc = desc.substring(1);
			break;
		case 'I':
			type = "int";
			desc = desc.substring(1);
			break;
		case 'J':
			type = "long";
			desc = desc.substring(1);
			break;
		case 'S':
			type = "short";
			desc = desc.substring(1);
			break;
		case 'V':
			type = "void";
			desc = desc.substring(1);
			break;
		case 'Z':
			type = "boolean";
			desc = desc.substring(1);
			break;
		case 'L':
			int semiIndex = desc.indexOf(';');
			type = InstrumentationAgent.sourceName(desc.substring(1, semiIndex));
			desc = desc.substring(semiIndex + 1);
			break;
		default:
			Util.fail("Bad descriptor " + desc);
			type = "";
			break;
		}
		
		return new Pair<String,String>(type + arrayType, desc);
	}
}
