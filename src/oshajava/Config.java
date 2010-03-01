package oshajava;

import oshajava.instrument.InstrumentationAgent;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;

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
 * TODO: make this or a separate OshaJavaPreMain the Pre-Main-Class to avoid
 * accidental dependencies from InstrumentationAgent...
 * @author bpw
 *
 */
public class Config {

	public static final CommandLineOption<Boolean> arrayIndexStatesOption =
		CommandLine.makeBoolean("arrayIndexStates", false, "");
	
	public static final CommandLineOption<Boolean> objectStatesOption = 
		CommandLine.makeBoolean("objectStates", false, "");
	
	public static final CommandLineOption<Boolean> nonCommAsDefaultOption =
		CommandLine.makeBoolean("defaultToNonComm", false, "");
	
	public static final CommandLineOption<Boolean> profileOption =
		CommandLine.makeBoolean("profile", false, "");
	
	public static final CommandLineOption<Integer> arrayCacheSizeOption =
		CommandLine.makeInteger("arrayCacheSize", 16, "");

	public static final CommandLineOption<Integer> lockCacheSizeOption =
		CommandLine.makeInteger("lockCacheSize", 4, "");
	
	public static final CommandLineOption<Boolean> fudgeExceptionTracesOption =
		CommandLine.makeBoolean("fudgeExceptionStackTraces", false, "");

	public static final CommandLine cl = new CommandLine("oshajava", "",
			arrayIndexStatesOption,
			objectStatesOption,
			nonCommAsDefaultOption,
			profileOption,
			arrayCacheSizeOption,
			lockCacheSizeOption,
			InstrumentationAgent.bytecodeDumpOption,
			InstrumentationAgent.bytecodeDumpDirOption,
			InstrumentationAgent.framesOption,
			InstrumentationAgent.fullJDKInstrumentationOption,
			InstrumentationAgent.preVerifyOption,
			InstrumentationAgent.verifyOption
	);

}
