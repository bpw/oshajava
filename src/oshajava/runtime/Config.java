package oshajava.runtime;

import java.lang.instrument.Instrumentation;

import oshajava.instrument.InstrumentationAgent;
import oshajava.rtviz.StackCommMonitor;
import oshajava.support.acme.util.StringMatchResult;
import oshajava.support.acme.util.StringMatcher;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.acme.util.option.CommandLineOption.Kind;
import oshajava.support.acme.util.option.Option;
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
	
//	public static final Option<String> oshajavaRevision = new Option<String>("revision", "$Revision$".replace("$Revision: ", "").replace(" $", ""));
	
	// -- Options ---------------------------------------------------------
	
	public static final CommandLineOption<Boolean> helpOption =
		CommandLine.makeBoolean("help", false, Kind.STABLE, "Show help.");
	
	public static final CommandLineOption<String> jvmOption =
		CommandLine.makeString("java", "java", Kind.STABLE, "JVM to use. (Handled by wrapper script.)");
	
	public static final CommandLineOption<Boolean> intraThreadOption =
		CommandLine.makeBoolean("intraThread", false, Kind.EXPERIMENTAL, "Track all (intra- and inter-thread) communication.");

	public enum Granularity { FINE, COARSE }; // TODO add NONE level.
	public static final CommandLineOption<Granularity> arrayTrackingOption =
		CommandLine.makeEnumChoice("arrayTracking", Granularity.FINE, Kind.STABLE, "Set array tracking granularity.", Granularity.class);
	
	public static final CommandLineOption<Granularity> objectTrackingOption = 
		CommandLine.makeEnumChoice("objectTracking", Granularity.FINE, Kind.EXPERIMENTAL, 
				"Set object tracking granularity. (COARSE is not fully implemented.)", Granularity.class);
	
	public static final CommandLineOption<Boolean> noInstrumentOption =
		CommandLine.makeBoolean("noTracking", false, Kind.STABLE, "Turn off all tracking.");
	
	public enum ProfileLevel { NONE, PERF, DEEP }
	public static final CommandLineOption<ProfileLevel> profileOption =
		CommandLine.makeEnumChoice("profile", ProfileLevel.NONE, Kind.STABLE, "Report tool profiling information. " + 
				"PERF reports timing and memory use, causing no additional overhead. " + 
				"DEEP reports many internal counters, causing significant performance overhead.", ProfileLevel.class);
	
	public static final CommandLineOption<Integer> arrayCacheSizeOption =
		CommandLine.makeInteger("arrayCacheSize", 16, Kind.STABLE, "Set the array state (or array state array) cache size.");

	public static final CommandLineOption<Integer> lockCacheSizeOption =
		CommandLine.makeInteger("lockCacheSize", 4, Kind.STABLE, "Set the lock state cache size.");
	
	public static final CommandLineOption<Boolean> fudgeExceptionTracesOption =
		CommandLine.makeBoolean("fudgeExceptionStackTraces", true, Kind.STABLE, "Make communication exceptions look like they occur directly in user code.");

    public static final CommandLineOption<Boolean> stackTracesOption =
        CommandLine.makeBoolean("traces", false, Kind.STABLE, "Store the full (pre-inlined) stack trace on every write for exception reports.");
    
    public enum ErrorAction { HALT, THROW, WARN, NONE }
    public static final CommandLineOption<ErrorAction> errorActionOption =
        CommandLine.makeEnumChoice("errorAction", ErrorAction.HALT, Kind.STABLE, "What to do when illegal communication occurs.", ErrorAction.class);
    
    public static final CommandLineOption<StringMatcher> noSpecOption =
    	CommandLine.makeStringMatcher("nospecs", StringMatchResult.NOTHING, Kind.EXPERIMENTAL, 
    			"Proceed silently if modules for methods in these classes cannot be found.", "+^java\\..*", "+^com.sun\\..*", "+^sun\\..*");
    
    public enum DefaultSpec { INLINE, NONCOMM, UNTRACKED }
    public static final CommandLineOption<DefaultSpec> noSpecActionOption =
    	CommandLine.makeEnumChoice("defaultSpec", DefaultSpec.INLINE, Kind.EXPERIMENTAL, "Default treatment of methods without specs.", DefaultSpec.class);
    
    public static final CommandLineOption<Boolean> recordOption =
    	CommandLine.makeBoolean("record", false, Kind.STABLE, "Record exercised graph and dump an XML file.");
    
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

    public static final CommandLine cl = new CommandLine(TOOL_NAME, "[ -javaOptions java options ] -- Program [ program args ]", helpOption, jvmOption);
	
	public static void configure(String[] args){
		// add command line options here --------------------------------------------------

		cl.addGroup("Error reporting");
		
		cl.add(errorActionOption);
		cl.add(stackTracesOption);

		cl.addGroup("Tracking");
		
		cl.add(noInstrumentOption);
		cl.add(arrayTrackingOption);
		cl.add(objectTrackingOption);
		cl.add(InstrumentationAgent.instrumentClassesOption);
		cl.add(InstrumentationAgent.instrumentFieldsOption);
		cl.add(InstrumentationAgent.instrumentMethodsOption);
		cl.add(InstrumentationAgent.volatileShadowOption);
		cl.add(intraThreadOption);
		
		cl.addGroup("Specification handling");
		
		cl.add(noSpecOption);
		cl.add(noSpecActionOption);
		
		cl.addGroup("Optimizations");
		
		cl.add(arrayCacheSizeOption);
		cl.add(lockCacheSizeOption);
		
		cl.addGroup("Profiling");
		
		cl.add(profileOption);
		cl.add(recordOption);
		cl.add(visualizeOption);
		cl.add(summaryOption);
		cl.add(createOption);
		cl.add(profileExtOption);
		cl.add(idOption);
		
		cl.addGroup("Instrumentation (for debugging oshajava)");
		
		cl.add(InstrumentationAgent.bytecodeDumpOption);
		cl.add(InstrumentationAgent.bytecodeDumpDirOption);
		cl.add(InstrumentationAgent.framesOption);
		cl.add(InstrumentationAgent.preVerifyOption);
		cl.add(InstrumentationAgent.verifyOption);
		
		cl.addGroup("Deprecated");
		cl.add(InstrumentationAgent.fullJDKInstrumentationOption);
		cl.add(InstrumentationAgent.ignoreMissingMethodsOption);
		
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
			@SuppressWarnings("unused")
			Object _ = StackCommMonitor.def; // Initialize real-time visualization system.
		}
		if( !CommandLine.javaArgs.get().replaceAll(" ", "").isEmpty()) {
			Util.logf("remaining args are \"%s\"", CommandLine.javaArgs.get());
			helpOption.set(true);
		}
		if (!noInstrumentOption.get()) {
			InstrumentationAgent.install(inst);
		}
		premainTimer.stop();
		premainFiniTimer.start();
	}

}
