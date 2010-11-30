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

package oshajava.runtime;

import java.lang.instrument.Instrumentation;

import oshajava.instrument.Filter;
import oshajava.instrument.Agent;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.support.acme.util.StringMatchResult;
import oshajava.support.acme.util.StringMatcher;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.acme.util.option.CommandLineOption.Kind;
import oshajava.util.count.ConcurrentTimer;
import oshajava.util.count.SequentialTimer;

/**
 * Options may be created here or in any class, but must be listed as args
 * to the CommandLine constructor to actually be used.
 * 
 * NOTE: any option referenced here will force the class in which it is
 * declared to be loaded before any options have been set.  This means
 * that any constants in such loaded classes whose values are derived from 
 * options will always get the default value. BE CAREFUL. Also think about 
 * transitive load dependencies...
 * 
 * In general, I'm trying to keep any runtime classes from loading before
 * options are set.  This gives us the opportunity to set constants based
 * on options and get better performance if they are heavily used.
 * 
 * @author bpw
 *
 */
public class Config {
	
	// -- Constants -------------------------------------------------------
	
	public static final String TOOL_NAME = "oshajava";
	
	// -- Options ---------------------------------------------------------
	
	public static final CommandLineOption<Boolean> helpOption =
		CommandLine.makeBoolean("help", false, Kind.STABLE, "Show help.");
	
	public static final CommandLineOption<String> jvmOption =
		CommandLine.makeString("java", "java", Kind.STABLE, "JVM to use. (Handled by wrapper script.)");
	
	public static final CommandLineOption<Boolean> intraThreadOption =
		CommandLine.makeBoolean("intraThread", false, Kind.EXPERIMENTAL, "Track all (intra- and inter-thread) communication.");

	public enum Granularity { FINE, COARSE }; // TODO add NONE level.
	public static final CommandLineOption<Granularity> arrayTrackingOption =
		CommandLine.makeEnumChoice("arrayTracking", Granularity.FINE, Kind.STABLE, "Set array tracking granularity. Default: " + Granularity.FINE, Granularity.class);
	
	public static final CommandLineOption<Granularity> objectTrackingOption = 
		CommandLine.makeEnumChoice("objectTracking", Granularity.FINE, Kind.EXPERIMENTAL, 
				"Set object tracking granularity. Default: " + Granularity.FINE + " (COARSE is not fully implemented.)", Granularity.class);
	
	public static final CommandLineOption<Boolean> lockTrackingOption =
		CommandLine.makeBoolean("lockTracking", false, Kind.STABLE, "Treat acquire/release/wait synchronization as communication.");
	
	public static final CommandLineOption<Boolean> noInstrumentOption =
		CommandLine.makeBoolean("noTracking", false, Kind.STABLE, "Turn off all tracking.");
	
	public enum ProfileLevel { NONE, PERF, DEEP }
	public static final CommandLineOption<ProfileLevel> profileOption =
		CommandLine.makeEnumChoice("profile", ProfileLevel.NONE, Kind.STABLE, "Report tool profiling information. Default: " + ProfileLevel.NONE + ". " + 
				"PERF reports timing and memory use, causing no additional overhead. " + 
				"DEEP reports many internal counters, causing significant performance overhead.", ProfileLevel.class);
	
	public static final CommandLineOption<Integer> arrayCacheSizeOption =
		CommandLine.makeInteger("arrayCacheSize", 16, Kind.STABLE, "Set the array state (or array state array) cache size. Default: " + 16);

	public static final CommandLineOption<Integer> lockCacheSizeOption =
		CommandLine.makeInteger("lockCacheSize", 4, Kind.STABLE, "Set the lock state cache size. Default: " + 4);
	
	public static final CommandLineOption<Boolean> fudgeExceptionTracesOption =
		CommandLine.makeBoolean("fudgeExceptionStackTraces", true, Kind.STABLE, "Make communication exceptions look like they occur directly in user code.");

    public static final CommandLineOption<Boolean> stackTracesOption =
        CommandLine.makeBoolean("traces", false, Kind.STABLE, "Store the full (pre-inlined) stack trace on every write for exception reports. (Expensive.)");
    
    public enum ErrorAction { HALT, THROW, WARN, NONE }
    public static final CommandLineOption<ErrorAction> errorActionOption =
        CommandLine.makeEnumChoice("errorAction", ErrorAction.HALT, Kind.STABLE, "What to do when illegal communication occurs. Default: " + ErrorAction.HALT, ErrorAction.class);
    
    // TODO
    public static final CommandLineOption<StringMatcher> noSpecOption =
    	CommandLine.makeStringMatcher("nospecs", StringMatchResult.NOTHING, Kind.EXPERIMENTAL, 
    			"Proceed silently if modules for methods in these classes cannot be found.", "+^java\\..*", "+^com.sun\\..*", "+^sun\\..*");
    
    // TODO
    public enum DefaultSpec { INLINE, NONCOMM, UNTRACKED }
    public static final CommandLineOption<DefaultSpec> noSpecActionOption =
    	CommandLine.makeEnumChoice("defaultSpec", DefaultSpec.INLINE, Kind.EXPERIMENTAL, "Default treatment of methods without specs.", DefaultSpec.class);
    
    public static final CommandLineOption<Boolean> recordOption =
    	CommandLine.makeBoolean("record", false, Kind.STABLE, "Record exercised graph and dump an XML file.");
    
    // TODO Deal or axe.
	public static final CommandLineOption<Boolean> visualizeOption =
		CommandLine.makeBoolean("visualize", false, Kind.EXPERIMENTAL, "Visualize communications in real-time.");
    
    public static final CommandLineOption<String> profileExtOption =
    	CommandLine.makeString("profileExt", "-oshajava-profile.py", Kind.STABLE, "Extension on profile file (prefixed by main class)");
    
    public static final CommandLineOption<Boolean> createOption =
    	CommandLine.makeBoolean("create", false, Kind.STABLE, "Create a full execution graph (text).");
    
    public static final CommandLineOption<Boolean> summaryOption =
		CommandLine.makeBoolean("summary", false, Kind.STABLE, "Print summary.");
    
    public static final CommandLineOption<String> idOption =
    	CommandLine.makeString("id", Long.toString(System.currentTimeMillis()), Kind.STABLE, "ID for this run.");
    
    // TODO improve
    public static final CommandLineOption<Boolean> shadowStoreGCoption =
    	CommandLine.makeBoolean("shadowStoreGC", false, Kind.STABLE, "Turn on garbage collection of expired keys in shadow stores.");
    
    /************/

    public static final CommandLine cl = new CommandLine(TOOL_NAME, "[ -javaOptions java options ] -- Program [ program args ]", helpOption, jvmOption);
	
	public static void configure(String[] args){
		// add command line options here --------------------------------------------------

		cl.addGroup("Error Reporting");
		
		cl.add(errorActionOption);
		cl.add(stackTracesOption);

		cl.addGroup("Tracking");
		
		cl.add(noInstrumentOption);
		cl.add(arrayTrackingOption);
		cl.add(objectTrackingOption);
		cl.add(lockTrackingOption);
//		cl.add(InstrumentationAgent.ignoreFinalFieldsOption);
		cl.add(Filter.instrumentClassesOption);
		cl.add(Filter.instrumentFieldsOption);
		cl.add(Filter.instrumentMethodsOption);
		cl.add(Agent.volatileShadowOption);
		cl.add(intraThreadOption);
		
		cl.addGroup("Specification Handling");
		
		cl.add(noSpecOption);
		cl.add(noSpecActionOption);
		
		cl.addGroup("Optimizations");
		
		cl.add(arrayCacheSizeOption);
		cl.add(lockCacheSizeOption);
		cl.add(shadowStoreGCoption);
		
		cl.addGroup("Profiling");
		
		cl.add(profileOption);
		cl.add(recordOption);
//		cl.add(visualizeOption);
		cl.add(summaryOption);
		cl.add(createOption);
		cl.add(profileExtOption);
		cl.add(idOption);
		
		cl.addGroup("Instrumentation");
		
		cl.add(Agent.remapOption);
		cl.add(Agent.bytecodeDumpOption);
		cl.add(Agent.bytecodeDumpDirOption);
//		cl.add(Agent.framesOption);
		cl.add(Agent.preVerifyOption);
		cl.add(Agent.verifyOption);
		
		cl.addGroup("Deprecated");
//		cl.add(InstrumentationAgent.fullJDKInstrumentationOption);
		cl.add(Agent.ignoreMissingMethodsOption);
		
		// end command line options -------------------------------------------------------
			
		cl.apply(args);	
	}
	protected static ConcurrentTimer premainTimer = new ConcurrentTimer("Premain time");
	protected static SequentialTimer premainFiniTimer = new SequentialTimer("Premain to fini time");
	public static void premain(String agentArgs, Instrumentation inst) {
		premainTimer.start();
		Thread.currentThread().setName(TOOL_NAME);
//		Util.logf("oshajava %s", oshajavaRevision.get());
		configure(agentArgs == null ? new String[0] : agentArgs.replace('#', ' ').split(","));
		if (visualizeOption.get()) {
//			@SuppressWarnings("unused")
//			Object _ = StackCommMonitor.def; // Initialize real-time visualization system.
		}
		if( !CommandLine.javaArgs.get().replaceAll(" ", "").isEmpty()) {
			Util.logf("remaining args are \"%s\"", CommandLine.javaArgs.get());
			helpOption.set(true);
		}
		if (!noInstrumentOption.get()) {
			try {
				Debug.debug(Agent.DEBUG_KEY, "Installing oshajava instrumentation agent.");
				inst.addTransformer(new Agent());
				Debug.debug(Filter.DEBUG_KEY, "Registering preloaded classes with filter mapper.");
//				Filter.init(inst);
			} catch (Throwable e) {
				Assert.fail("Problem installing oshajava instrumentor", e);
			}
		}
		premainTimer.stop();
		premainFiniTimer.start();
	}

}
