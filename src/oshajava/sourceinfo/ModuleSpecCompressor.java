package oshajava.sourceinfo;

import java.io.IOException;

import oshajava.util.ColdStorage;

/**
 * Post processor pass to run after compiler. Takes ModuleSpecBuilder files and
 * creates ModuleSpec files for them.
 * @author bpw
 *
 */
public class ModuleSpecCompressor {
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		for (final String s : args) {
			if (!s.endsWith(ModuleSpecBuilder.EXT)) {
				continue;
			}
			final ModuleSpecBuilder msb = ((ModuleSpecBuilder)ColdStorage.load(s));
			System.out.println("Compressing " + msb.getName());
			// msb.generateSpec().describe();
			ColdStorage.store(s.substring(0, s.lastIndexOf(ModuleSpecBuilder.EXT)) + ModuleSpec.EXT,
					msb.generateSpec());
		}
	}

}
