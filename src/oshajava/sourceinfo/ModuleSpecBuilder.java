package oshajava.sourceinfo;

import java.util.Vector;

public class ModuleSpecBuilder {
	
	// TODO suggestion: use this with nice auto-resizing vectors to build the specs. Then, when
	// all specs read in create ModuleSpecs (perfectly sized and faster access than vectors) and
	// add them to the final spec.
	
	private Vector<String> methodIdToSig = new Vector<String>();
		
	public ModuleSpecBuilder(String name) {
		
	}
}
