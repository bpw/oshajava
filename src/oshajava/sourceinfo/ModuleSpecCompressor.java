package oshajava.sourceinfo;

import java.io.IOException;

import oshajava.util.ColdStorage;
import oshajava.util.count.AbstractCounter;
import oshajava.util.count.DistributionCounter;
import oshajava.util.PyWriter;

/**
 * Post processor pass to run after compiler. Takes ModuleSpecBuilder files and
 * creates ModuleSpec files for them.
 * @author bpw
 *
 */
public class ModuleSpecCompressor {
	public static void main(String[] args) throws IOException, ClassNotFoundException {
	    boolean stats = false;
	    
	    DistributionCounter methods = new DistributionCounter("Total methods");
	    DistributionCounter inlinedMethods = new DistributionCounter("Inlined methods");
	    DistributionCounter communicationGroups = new DistributionCounter("Communication groups");
	    DistributionCounter interfaceGroups = new DistributionCounter("Interface groups");
	    DistributionCounter memberships = new DistributionCounter("Total group memberships");
	    DistributionCounter totalAnnotations = new DistributionCounter("Source annotations");
	    
		for (final String s : args) {
			if (!s.endsWith(ModuleSpecBuilder.EXT)) {
			    if (s.equals("-stats"))
			        stats = true;
				continue;
			}
			final ModuleSpecBuilder msb = ((ModuleSpecBuilder)ColdStorage.load(s));
			final ModuleSpec ms = msb.generateSpec();
			
			if (stats) {
			    methods.add(ms.methodSigToId.size());
			    inlinedMethods.add(ms.inlinedMethods.size());
			    
			    int cg = 0;
			    int ig = 0;
			    for (ModuleSpecBuilder.Group g : msb.groups.values()) {
			        if (g.isInterfaceGroup)
			            ig++;
			        else
			            cg++;
			    }
			    communicationGroups.add(cg);
			    interfaceGroups.add(ig);
			    
			    memberships.add(msb.memberships.size());
			    totalAnnotations.add(msb.totalAnnotations);
			}

			System.out.println("Compressing " + msb.getName());
    		
			ColdStorage.store(s.substring(0, s.lastIndexOf(ModuleSpecBuilder.EXT)) + ModuleSpec.EXT, ms);
		}
		
		if (stats) {
		    //////
		    PyWriter writer = new PyWriter("modulestats.py", false);
		    writer.startMap();
			writer.writeCounterAsMapPair(methods);
			writer.writeCounterAsMapPair(inlinedMethods);
			writer.writeCounterAsMapPair(communicationGroups);
			writer.writeCounterAsMapPair(interfaceGroups);
			writer.writeCounterAsMapPair(memberships);
			writer.writeCounterAsMapPair(totalAnnotations);
		    writer.endMap();
		    writer.close();
		}
		
	}

}
