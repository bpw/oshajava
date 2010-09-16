package oshajava.runtime;

import java.lang.instrument.Instrumentation;

import oshajava.instrument.InstrumentationAgent;
import oshajava.rtviz.StackCommMonitor;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
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
		CommandLine.makeBoolean("help", false, "Show help.");
	
	public static final CommandLineOption<String> jvmOption =
		CommandLine.makeString("java", "java", "JVM to use.");
	
	public static final CommandLineOption<Boolean> intraThreadOption =
		CommandLine.makeBoolean("intraThread", false, "Track all (intra- and inter-thread) communication. (Experimental)");

	public enum Granularity { FINE, COARSE };
	public static final CommandLineOption<Granularity> arrayTrackingOption =
		CommandLine.makeEnumChoice("arrayTracking", Granularity.FINE, "Set array tracking granularity.", Granularity.class);
	
	public static final CommandLineOption<Granularity> objectTrackingOption = 
		CommandLine.makeEnumChoice("objectTracking", Granularity.FINE, "Set object tracking granularity. (COARSE is not fully implemented.)", Granularity.class);
	
	public static final CommandLineOption<Boolean> noInstrumentOption =
		CommandLine.makeBoolean("noInstrument", false, "Turn off instrumentation.");
	
	public enum ProfileLevel { NONE, PERF, DEEP }
	public static final CommandLineOption<ProfileLevel> profileOption =
		CommandLine.makeEnumChoice("profile", ProfileLevel.NONE, "Report tool profiling information. " + 
				"PERF reports timing and memory use, causing no additional overhead. " + 
				"DEEP reports many internal counters, causing significant performance overhead.", ProfileLevel.class);
	
	public static final CommandLineOption<Integer> arrayCacheSizeOption =
		CommandLine.makeInteger("arrayCacheSize", 16, "Set the array state (or array state array) cache size.");

	public static final CommandLineOption<Integer> lockCacheSizeOption =
		CommandLine.makeInteger("lockCacheSize", 4, "Set the lock state cache size.");
	
	public static final CommandLineOption<Boolean> fudgeExceptionTracesOption =
		CommandLine.makeBoolean("fudgeExceptionStackTraces", true, "Make communication exceptions look like they occur directly in user code.");

    public static final CommandLineOption<Boolean> stackTracesOption =
        CommandLine.makeBoolean("traces", false, "Save writer stack traces for debugging violations.");
    
    public enum ErrorAction { HALT, THROW, WARN, NONE }
    public static final CommandLineOption<ErrorAction> errorActionOption =
        CommandLine.makeEnumChoice("errorAction", ErrorAction.HALT, "What to do when illegal communication occurs.", ErrorAction.class);
    
    public static final CommandLineOption<Boolean> recordOption =
    		CommandLine.makeBoolean("record", false, "Record exercised graph.");
    
	public static final CommandLineOption<Boolean> visualizeOption =
		CommandLine.makeBoolean("visualize", false, "Visualize communications in real-time. (Partly broken)");
    
    public static final CommandLineOption<String> profileExtOption =
    	CommandLine.makeString("profileExt", "-oshajava-profile.py", "Extension on profile file (prefixed by main class)");
    
    public static final CommandLineOption<Boolean> createOption =
    		CommandLine.makeBoolean("create", false, "Create a full execution graph.");
    
    public static final CommandLineOption<Boolean> summaryOption =
		CommandLine.makeBoolean("summary", false, "Print summary.");
    
    public static final CommandLineOption<String> idOption =
    	CommandLine.makeString("id", Long.toString(System.currentTimeMillis()), "ID for this run.");

    public static final CommandLine cl = new CommandLine(TOOL_NAME, "[ -javaOptions java options ] -- Class [ class args ]", helpOption, jvmOption);
	
	public static void configure(String[] args){
		// add command line options here --------------------------------------------------

		cl.addGroup("Tracking");
		
		cl.add(arrayTrackingOption);
		cl.add(objectTrackingOption);
		cl.add(intraThreadOption);
		
		cl.addGroup("Profiling");
		
		cl.add(profileOption);
		cl.add(recordOption);
		cl.add(visualizeOption);
		cl.add(summaryOption);
		cl.add(createOption);
		cl.add(profileExtOption);
		cl.add(idOption);
		
		cl.addGroup("Optimizations");
		
		cl.add(arrayCacheSizeOption);
		cl.add(lockCacheSizeOption);
		
		cl.addGroup("Error reporting");
		
		cl.add(stackTracesOption);
		cl.add(errorActionOption);

		cl.addGroup("Instrumentation");
		
		cl.add(noInstrumentOption);
		cl.add(InstrumentationAgent.bytecodeDumpOption);
		cl.add(InstrumentationAgent.bytecodeDumpDirOption);
		cl.add(InstrumentationAgent.framesOption);
		cl.add(InstrumentationAgent.fullJDKInstrumentationOption);
		cl.add(InstrumentationAgent.preVerifyOption);
		cl.add(InstrumentationAgent.verifyOption);
		cl.add(InstrumentationAgent.ignoreMissingMethodsOption);
		cl.add(InstrumentationAgent.volatileShadowOption);

		// end command line options -------------------------------------------------------
			
		cl.apply(args);	
	}
	protected static ConcurrentTimer premainTimer = new ConcurrentTimer("Premain time");
	protected static SequentialTimer premainFiniTimer = new SequentialTimer("Premain to fini time");
	public static void premain(String agentArgs, Instrumentation inst) {
		premainTimer.start();
		Thread.currentThread().setName(TOOL_NAME);
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
