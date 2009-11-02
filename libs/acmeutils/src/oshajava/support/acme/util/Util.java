/******************************************************************************

Copyright (c) 2009, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

******************************************************************************/

package oshajava.support.acme.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import oshajava.support.acme.util.collections.ArrayIterator;
import oshajava.support.acme.util.io.NamedFileWriter;
import oshajava.support.acme.util.io.SplitOutputWriter;
import oshajava.support.acme.util.option.CommandLine;
import oshajava.support.acme.util.option.CommandLineOption;
import oshajava.support.acme.util.time.SuppressTimedStmt;
import oshajava.support.acme.util.time.TimedExpr;
import oshajava.support.acme.util.time.TimedStmt;


public class Util {

	private static class ThreadStatus {
		int logLevel = 0;
		int newLineCount = 0;
		int suppressLevel = 0;
	}

	private static final ThreadLocal<ThreadStatus> threadStatus = new ThreadLocal<ThreadStatus>() {
		@Override
		protected ThreadStatus initialValue() {
			return new ThreadStatus();
		}
	};

	private static int numWarnings = 0;
	private static int numYikes = 0;
	private static boolean failed = false;

	public static final String ERROR_PREFIX = "## ";
	private static final int YIKES_MAX = 1;

	public static final CommandLineOption<Boolean> quietOption = 
		CommandLine.makeBoolean("quiet", false, "Quiet mode.  Will not print debugging or logging messages.");

	public static final CommandLineOption<ArrayList<String>> debugKeysOption = 
		CommandLine.makeStringList("d", "Turn on the given debugging key.  Messages printed by Util.debugf(key, ...) will only be printed if the key is turned on.");

	public static CommandLineOption<String> outputPathOption = 
		CommandLine.makeString("logs", "log", "The path to the directory where log files will be stored.");

	public static CommandLineOption<String> outputFileOption = 
		CommandLine.makeString("out", "", "Log file name for Util.out.",
				new Runnable() { public void run() {  
					String errFile = errorFileOption.get();
					String outFile = outputFileOption.get();
					if (errFile.equals(outFile)) {
						Util.out = Util.err;
					} else {
						assertTrue(outFile.length() > 0, "Bad File");
						setOut(new PrintWriter(new SplitOutputWriter(out, Util.openLogFile(outFile)), true));
					} 
				} } );

	public static CommandLineOption<String> errorFileOption = 
		CommandLine.makeString("err", "", "Log file name for Util.err.",
				new Runnable() { public void run() {  
					String errFile = errorFileOption.get();
					String outFile = outputFileOption.get();
					if (errFile.equals(outFile)) {
						Util.err = Util.out;
					} else {
						assertTrue(errFile.length() > 0, "Bad File");
						setErr(new PrintWriter(new SplitOutputWriter(err, Util.openLogFile(errFile)), true));
					}
				} } );

	public static void debug(String key, String s) {
		if (debugKeysOption.get().contains(key)) {
			log(key + "-- " + s);
		}
	}

	public static void debugf(String key, String format, Object... args) {
		if (debugKeysOption.get().contains(key)) {
			logf(key + "-- " + format, args);
		}
	}

	public static void debug(String key, boolean guard, String s) {
		if (guard && debugKeysOption.get().contains(key)) {
			log(key + "-- " + s);  
		}
	}

	public static boolean debugOn(String key) {
		return debugKeysOption.get().contains(key);
	}

	public static void debug(String key, Runnable op) {
		if (debugKeysOption.get().contains(key)) {
			op.run();
		}
	}

	public static void error(String format, Object... args) {
		synchronized(Util.class) {
			Util.err.println(String.format(ERROR_PREFIX + format, args).replaceAll("\n", "\n" + ERROR_PREFIX));
		}
	}

	public static void error(Object o) {
		error("%s", o);
	}

	public static void printf(String format, Object... args) {
		synchronized(Util.class) {
			ThreadStatus status = threadStatus.get();
			pad(status);
			String msg = String.format(format, args);
			if (!msg.endsWith("\n")) {
				msg += "\n";
			}
			Util.out.printf("%s", msg);
		}
	}


	public static void println(Object s) {
		Util.printf("%s\n", s);
	}

	public static void warn(String format, Object... args) {
		synchronized(Util.class) {
			ThreadStatus status = threadStatus.get();
			pad(status);
			error("WARNING: " + format, args);
			Util.err.println();
			numWarnings++;
		}
	}


	private static HashMap<String, Integer> yikesMessages = new HashMap<String,Integer>();

	public static boolean yikes(String format, Object... args) {
		synchronized(Util.class) {
			String msg = String.format(format, args);
			Integer n = yikesMessages.get(msg);
			if (n == null) {
				n = 1;
			} else {
				n++;
			}
			yikesMessages.put(msg, n);
			if (n < YIKES_MAX) {
				ThreadStatus status = threadStatus.get();
				pad(status);
				error("YIKES: " + msg);
				if (n== YIKES_MAX - 1) {
					error("Suppressing further yikes messages like that one."); 
				}
				Util.err.println();	 
				return true;
			} else {
				return false;
			}
		}
	}

	public static boolean yikes(Throwable e) {
		return yikes(e.toString(), e);
	}

	public static boolean yikes(Object o) {
		return yikes("%s", o.toString());
	}

	public static boolean yikes(String s, Throwable e) {
		synchronized(Util.class) {
			boolean b = yikes("%s ", s);
			if (b) {
				error("\n");
				printStack(Util.err, e, ERROR_PREFIX);
				Throwable cause = e.getCause();
				if (cause != null) {
					error("Caused by...\n %s \n", cause.getMessage());
					printStack(Util.err, cause, ERROR_PREFIX);
				}
				Util.err.flush();
			}
			return b;
		}
	}


	public static void assertTrue(boolean b) {
		if (!b) {
			fail("Assertion Failure");
		}
	}

	public static void assertTrue(boolean b, String s) {
		if (!b) {
			fail("Assertion Failure: %s", s);
		}
	}

	public static void assertTrue(boolean b, String s, Object... args) {
		if (!b) {
			fail("Assertion Failure: " + s, args);
		}
	}

	public static void assertHoldsLock(Object l) {
		Util.assertTrue(Thread.holdsLock(l));
	}

	public static void assertHoldsLock(Object l, String s, Object args) {
		Util.assertTrue(Thread.holdsLock(l), s, args);
	}

	public static void fail(Throwable e) {
		fail(e.toString(), e);
	}

	public static void fail(String s, Object... args) {
		fail(String.format(s, args), new Throwable()); 
	}

	public static void fail(String s, Throwable e) {
		failed = true;
		synchronized(Util.class) {
			error("\n");
			error("%s ", s);
			error("\n");
			printStack(Util.err, e, ERROR_PREFIX);
			Throwable cause = e.getCause();
			if (cause != null) {
				error("Caused by...\n %s \n", cause.getMessage());
				printStack(Util.err, cause, ERROR_PREFIX);
			}
			Util.err.flush();
		}
		Util.exit(1);
	}

	public static void panic(String s) {
		failed = true;
		error("\n");
		error("PANIC %s\n", s);
		printStack(Util.err, new Throwable(), ERROR_PREFIX);
		error("\n");
		Runtime.getRuntime().halt(17);
	}

	public static void panic(String s, Object... args) {
		panic(String.format(s, args)); 
	}

	public static void panic(Throwable e) {
		failed = true;
		error("\n");
		error("PANIC %s\n", e.toString());
		printStack(Util.err, e, ERROR_PREFIX);
		Throwable cause = e.getCause();
		if (cause != null) {
			error("Caused by...\n");
			error("%s\n", cause.getMessage());
			printStack(Util.err, cause, ERROR_PREFIX);
		}
		Util.err.flush();
		Runtime.getRuntime().halt(17);
	}


	public static boolean failed() {
		return failed;
	}


	/*****************/

	public static int getNumWarnings() {
		return numWarnings;
	}

	public static boolean getFailed() {
		return failed;
	}

	public static int getNumYikes() {
		return numYikes;
	}

	/*****************/

	public static void printStack(PrintWriter out) {
		printStack(out, new Throwable(), "");
	}

	public static void printStack(PrintWriter out, Throwable e, String prefix) {
		Iterator<StackTraceElement> iter = new ArrayIterator<StackTraceElement>(e.getStackTrace());
		out.println(prefix);
		while (iter.hasNext()) {
			StackTraceElement t = iter.next();
			if (!t.getClassName().equals("acme.util.Util")) {
				out.print(prefix);
				out.println("    " + t);
				break;
			}
		}
		while (iter.hasNext()) {
			StackTraceElement t = iter.next();
			out.print(prefix);
			out.println("    " + t);
		}
	}

	public void printStack() {
		printStack(Util.out);
	}

	public static void printAllStacks(int depth, PrintWriter out) {
		Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
		out.println("----------");			
		for (Entry<Thread,StackTraceElement[]> t : stacks.entrySet()) {
			Thread thread = t.getKey();
			StackTraceElement[] elems = t.getValue();
			out.printf("%-35s      state = %10s   depth = %5d \n", thread, thread.getState(), elems.length);
			int max = depth;
			for (StackTraceElement ste : elems) {
				out.println("    " + ste);
				if (max-- == 0) break;
			}
			out.println("----------");
		}
	}

	public static void printAllStacks(int depth) {
		printAllStacks(depth, Util.out);
	}

	/****************/


	private static void pad(ThreadStatus status) {

		if (status.newLineCount != 0) {
			out.println();
		}
		status.newLineCount = 0;
		for (int i = 0; i < status.logLevel; i++) {
			out.print("  ");
		}
	}

	public static <T> T log(TimedExpr<T> lo) throws Exception {
		ThreadStatus status = threadStatus.get();
		log(lo.toString());
		status.logLevel++;
		long time = System.currentTimeMillis();
		try {
			return lo.run();
		} finally {
			status.logLevel--;
			logf("%.3g sec",(System.currentTimeMillis() - time) / 1000.0);
		}
	}

	public static void log(TimedStmt lo) throws Exception {
		ThreadStatus status = threadStatus.get();
		log(lo.toString());
		status.logLevel++;
		long time = System.currentTimeMillis();
		try {
			lo.run();
		} finally {
			status.logLevel--;
			logf("%.3g sec",(System.currentTimeMillis() - time) / 1000.0);
		}
	}

	public static <T> T eval(TimedExpr<T> lo) throws Exception {
		ThreadStatus status = threadStatus.get();
		log(lo.toString());
		status.logLevel++;
		try {
			return lo.run();
		} finally {
			status.logLevel--;			
		}
	}

	public static void eval(TimedStmt lo) throws Exception {
		ThreadStatus status = threadStatus.get();
		log(lo.toString());
		status.logLevel++;
		try {
			lo.run();
		} finally {
			status.logLevel--;			
		}
	}

	public static void log(SuppressTimedStmt lo) {
		ThreadStatus status = threadStatus.get();
		log(lo.toString());
		status.logLevel++;
		status.suppressLevel++;
		long time = System.currentTimeMillis();
		try {
			lo.run();
		} finally {
			status.logLevel--;
			status.suppressLevel--;
			logf("%.3g sec",(System.currentTimeMillis() - time) / 1000.0);
		}
	}


	private static String prefix() {
		String prefix = Thread.currentThread().getName();
		if (prefix.equals("")) {
			prefix = "";
		} else {
			prefix += ": ";
		}
		return prefix;
	}

	public static void logf(String s, Object... ops) {
		ThreadStatus status = threadStatus.get();
		if (quietOption.get() || status.suppressLevel > 0) {
			return;
		}
		synchronized(Util.class) {
			pad(status);
			out.printf("[" + prefix() + s + "]\n", ops);
		}
	}

	public static synchronized void log(String s) {
		logf("%s", s);
	}

	public static synchronized void log(Object o) {
		log(o == null ? "null" : o.toString());
	}

	public static void lognl(String s) {
		ThreadStatus status = threadStatus.get();
		if (quietOption.get() || status.suppressLevel > 0) {
			return;
		}
		synchronized(Util.class) {
			if (status.newLineCount == 0) {
				pad(status);
			} else if (status.newLineCount == 8) {
				pad(status);
			}
			status.newLineCount++;
			out.print(s);
		}
	}

	public static void message(String s, Object... ops) {
		ThreadStatus status = threadStatus.get();
		synchronized(Util.class) {
			pad(status);
			out.printf("[" + prefix() + s + "]\n", ops);
		}
	}


	/*******/

	private static IdentityHashMap<Object,String> ids = new IdentityHashMap<Object,String>();

	public static String objectToIdentityString(Object target) {
		if (false) {
			return String.format("0x%08X (%s)", Util.identityHashCode(target), target.getClass());
		} else {
			synchronized(Util.class) {
				String x = ids.get(target);
				if (x == null) {
					x = String.format("@%02X", ids.size() + 1);
					ids.put(target, x);
				}
				return x;
			}
		}
	}

	public static String boxedValueToValueString(Object x) {
		if (x == null) {
			return "null";
		} else if (x instanceof Number || x instanceof Boolean) {
			return x.toString();
		} else if (x instanceof Character) {
			return "'" + x + "'";
		} else if (x instanceof String) {
			return objectToIdentityString(x) + "(\"" + x + "\")";
		} else {
			return objectToIdentityString(x);
		}
	}


	public static int identityHashCode(Object o) {
		return System.identityHashCode(o);
	}

	/*******/

	public static String stackDump() {
		return stackDump(Thread.currentThread(), null);
	}

	public static String stackDump(Thread thread) {
		return stackDump(thread, null);
	}


	public static String stackDump(Thread thread, StringMatcher systemCode) {
		String res = "";
		boolean inUser = false;
		for (StackTraceElement ste: thread.getStackTrace()) {
			String n = ste.getClassName();
			if (systemCode == null || systemCode.test(n) != StringMatchResult.ACCEPT) {
				inUser = true;
			}
			if (inUser) { 
				res += ste.toString().trim();
				res += "\n";
			}
		}
		return res;
	}

	public static String stackDump(Throwable t) {
		return stackDump(t, null);
	}

	public static String stackDump(Throwable t, StringMatcher systemCode) {
		String res = "";
		boolean inUser = false;
		if (t == null) {
			return "<no stack>";
		}
		for (StackTraceElement ste: t.getStackTrace()) {
			String n = ste.getClassName();
			if (systemCode == null || systemCode.test(n) != StringMatchResult.ACCEPT) {
				inUser = true;
			}
			if (inUser) { 
				res += ste.toString().trim();
				res += "\n";
			}
		}
		return res;
	}


	/******************/

	public static String getenv(String name, String defaultVal) {
		String p = System.getenv(name);
		if (p == null) {
			p = defaultVal;
		}
		return p;
	}


	/*****************/

	protected static class SyncPrintWriter extends PrintWriter {

		public SyncPrintWriter(PrintStream out) {
			super(out, true);
			this.lock = Util.class;
		}

		public SyncPrintWriter(Writer out) {
			super(out, true);
			this.lock = Util.class;
		}



	}

	static private PrintWriter out;
	static private PrintWriter err;

	static public void setOut(PrintWriter out) {
		Util.out = new SyncPrintWriter(out);
	}

	static public void setErr(PrintWriter err) {
		Util.err = new SyncPrintWriter(err);
	}

	static {
		err = new SyncPrintWriter(System.err);
		out = new SyncPrintWriter(System.out);

	}

	/******************/

	private static HashMap<String, Integer> counter = new HashMap<String, Integer>();

	public static synchronized String nextFileNameInSeries(String prefix, String suffix) {
		Integer i = counter.get(prefix);
		if (i == null) {
			i = 0;
		}
		String res = prefix + i + suffix;
		counter.put(prefix, i+1);
		return res;
	}

	/******************/

	static public String makeLogFileName(String relName) {
		String path = outputPathOption.get();
		if (!path.equals("") && path.charAt(path.length() - 1) != File.separatorChar) {
			path += File.separatorChar;
		}
		return path + relName;
	}

	static public NamedFileWriter openLogFile(String name) {
		try {	
			new File(outputPathOption.get()).mkdirs();
			return new NamedFileWriter(makeLogFileName(name));
		} catch (IOException e) {
			Util.fail(e);
			return null;
		}
	}		

	/******************/

	private static final Vector<PeriodicTaskStmt> periodicTasks = new Vector<PeriodicTaskStmt>();


	public static void addToPeriodicTasks(PeriodicTaskStmt s) {
		periodicTasks.add(s);
		if (periodicTasks.size() == 1) periodic.start();
	}

	private static Thread periodic = new Thread(new Runnable() {
		public void run() {
			while (true) {
				try {
					Thread.sleep(1000);
					Util.log(new TimedStmt("Periodic Tasks") {
						@Override
						public void run() {
							for (int i = 0; i < periodicTasks.size(); i++) {
								PeriodicTaskStmt p = periodicTasks.get(i);
								if (p.wantsToRunTask()) {
									p.runTask();
								}
							}
						}
					});
				} catch (Exception e) {
					panic(e);
				}

			}
		}
	},
	"Periodic Tasks");


	/******************/
	private static Vector<TimedStmt> runQueue = new Vector<TimedStmt>();

	private static boolean runningQueueAlready = false;
	private static int THREADS = 1;

	public static void addToExitRunQueue(TimedStmt s) {
		runQueue.add(s);
	}

	private static void runExitQueue() {
		Thread ts[] = new Thread[THREADS];
		for (int i = 0; i < THREADS; i++) {
			(ts[i] = new Thread(new Runnable() {
				public void run() {
					while (true) {
						TimedStmt st = null;
						synchronized (runQueue) {
							if (runQueue.size() == 0) {
								return;
							}
							st = runQueue.remove(0);
						}
						try {
							st.run();
						} catch (Exception e) {
							Util.panic(e);
						}
					}
				}})).start();
		}
		for (Thread t : ts) {
			try {
				t.join();
			} catch (InterruptedException e) {
				Util.fail(e);
			}
		}  
	}	

	public static void exit(int code) {
		Util.logf("Exiting: %d", code);
		//		Util.logf("Called From:");
		//		Util.printStack(Util.out, new Throwable(), "");
		if (!runningQueueAlready) {
			runningQueueAlready = true;
			runExitQueue();
			System.exit(code);
		}
	}

}