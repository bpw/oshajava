package oshajava.spec.names;

import java.util.HashMap;
import java.util.Map;

import oshajava.support.acme.util.Assert;

public class PrimitiveDescriptor extends TypeDescriptor {

	private static final long serialVersionUID = 1L;
	
	private static final Map<String,PrimitiveDescriptor> primitives = new HashMap<String,PrimitiveDescriptor>();
	static {
		String[][] primitiveTypes = {
				{"boolean", "Z"},
				{"byte", "B"},
				{"char", "C"},
				{"double", "D"},
				{"float", "F"},
				{"int", "I"},
				{"long", "J"},
				{"short", "S"},
				{"void", "V"}
		};
		for (String[] n : primitiveTypes) {
			PrimitiveDescriptor p = new PrimitiveDescriptor(n[0], n[1]);
			primitives.put(n[0], p);
			primitives.put(n[1], p);
		}
	}
	
	public static PrimitiveDescriptor get(String name) {
		PrimitiveDescriptor p =  primitives.get(name);
		Assert.assertTrue(p != null, "No such primitive %s!", name);
		return p;
	}
	
	private final String sourceDescriptor, internalDescriptor;
	
	private PrimitiveDescriptor(String sourceDescriptor, String internalDescriptor) {
		this.internalDescriptor = internalDescriptor;
		this.sourceDescriptor = sourceDescriptor;
	}

	@Override
	public String toSourceString() {
		return sourceDescriptor;
	}

	@Override
	public String toInternalString() {
		return internalDescriptor;
	}

}
