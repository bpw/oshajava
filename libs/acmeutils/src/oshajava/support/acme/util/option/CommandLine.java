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

package oshajava.support.acme.util.option;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Vector;

import oshajava.support.acme.util.StringMatchResult;
import oshajava.support.acme.util.StringMatcher;
import oshajava.support.acme.util.Strings;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.collections.IterableIterator;
import oshajava.support.acme.util.collections.Pair;



public class CommandLine {

	public static final Option<String> javaArgs = 
		new Option<String>("javaArgs", "");

	private Vector<CommandLineOption<?>> flags = new Vector<CommandLineOption<?>>();
	private final String requiredPart; 
	private final String commandName;

	private ArrayList<String> usageInfo = new ArrayList<String>();

	private ArrayList<Runnable> postProcess = new ArrayList<Runnable>();

	protected CommandLineConstraints constraints = new CommandLineConstraints();

	public static CommandLineOption<Boolean> makeBoolean(String id, boolean dV, String usage) {
		return new CommandLineOption<Boolean>(id,dV,false,usage) {
			protected void apply(String arg) {
				this.set(!this.defaultVal);
			}

		};
	}

	public static CommandLineOption<Boolean> makeBoolean(String id, boolean dV, String usage, final Runnable r) {
		return new CommandLineOption<Boolean>(id,dV,false,usage) {
			protected void apply(String arg) {
				this.set(!this.defaultVal);
				r.run();
			}

		};
	}


	public static CommandLineOption<Integer> makeInteger(String id, int dV, String usage) {
		return new CommandLineOption<Integer>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(Integer.parseInt(arg));
			}

		};
	}

	public static CommandLineOption<Integer> makeInteger(String id, int dV, String usage, final Runnable r) {
		return new CommandLineOption<Integer>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(Integer.parseInt(arg));
				r.run();
			}

		};
	}


	public static CommandLineOption<Long> makeLong(String id, long dV, String usage) {
		return new CommandLineOption<Long>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(Long.parseLong(arg));
			}

		};
	}

	public static CommandLineOption<Long> makeLong(String id, long dV, String usage, final Runnable r) {
		return new CommandLineOption<Long>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(Long.parseLong(arg));
				r.run();
			}

		};
	}


	public static CommandLineOption<String> makeString(String id, String dV, String usage) {
		return new CommandLineOption<String>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(arg);
			}

		};
	}


	public static CommandLineOption<String> makeAppendableString(String id, String dV, final String sep, String usage) {
		return new CommandLineOption<String>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(this.get() + (this.get().length() > 0 ? sep : "") + arg);
			}

		};
	}


	public static CommandLineOption<String> makeString(String id, String dV, String usage, final Runnable r) {
		return new CommandLineOption<String>(id,dV,true,usage) {
			protected void apply(String arg) {
				this.set(arg);
				r.run();
			}

		};
	}

	public static CommandLineOption<ArrayList<String>> makeStringList(String id, String usage) {
		return new CommandLineOption<ArrayList<String>>(id,new ArrayList<String>(),true,usage) {
			protected void apply(String arg) {
				this.get().add(arg);
			}		
			protected String getType() {
				return "String";
			}

		};
	}


	public static <T extends Enum<T>> CommandLineOption<T> makeEnumChoice(final String id, T initial, String usage, final Class<T> choices) {
		return new CommandLineOption<T>(id,initial,true,"One of " + Arrays.toString(choices.getEnumConstants()) + ".  " + usage) {
			protected void apply(String arg) {
				try {
					set(Enum.valueOf(choices, arg));
				} catch (IllegalArgumentException e) {
					Util.fail("Invalid option for " + id + ".  Must be one of " + Arrays.toString(choices.getEnumConstants()));
				}
			}

		};
	}

	public static CommandLineOption<Integer> 
	makeStringChoice(final String id, String initial, String usage, final String... choices) {
		for (int i = 0; i < choices.length; i++) {
			if (initial.equals(choices[i])) {
				return new CommandLineOption<Integer>(id,i,true,usage + " (One of:" + Arrays.toString(choices) + ")") {
					protected void apply(String arg) {
						for (int i = 0; i < choices.length; i++) {
							if (arg.equals(choices[i])) {
								this.set(i);
								return;
							}
						}
						Util.fail("Invalid choice for " + id + ". Must be one of: " + Arrays.toString(choices));
					}	
					protected String rep() {
						return choices[get()] + "("+get()+")";
					}
				};
			}
		}
		Util.fail("Invalid choice for " + id + ". Must be one of: " + Arrays.toString(choices));
		return null;
	}

	public static CommandLineOption<StringMatcher> makeStringMatcher(String id, final StringMatchResult defaultResult, 
			String usage, final String... initialArgs) {
		CommandLineOption<StringMatcher> tmp = new CommandLineOption<StringMatcher>(id, new StringMatcher(defaultResult), true, usage) {
			private final int defaultLen = initialArgs.length; 
			protected void apply(String arg) {
				char ch = arg.charAt(0);
				Util.assertTrue(ch == '+' || ch == '-', "match item '" + arg + "' must start with +/-");
				// arg += ".*";
				this.get().addNFromEnd(defaultLen, arg);
			}
		};
		for (int i = 0; i < initialArgs.length; i++) {
			String arg = initialArgs[i];
			char ch = arg.charAt(0);
			Util.assertTrue(ch == '+' || ch == '-', "match item '" + arg + "' must start with +/-");
			//	arg += ".*";
			tmp.get().add(arg);
		}
		return tmp;
	}	

	public <T> void add(CommandLineOption<T> c) { 

		String flag = c.getId();
		for (CommandLineOption<?> clo : flags) {
			if (clo.getId().equals(flag)) {
				Util.warn("Multiple Options with same flag: '%s'", flag);
				break;
			} 
		}

		flags.add(c);
		c.setCommandLine(this);
		usageInfo.add(c.getUsage());
	}

	public void addGroup(String name) {
		usageInfo.add(name);
		usageInfo.add(Strings.repeat("-", name.length()));
	}

	public CommandLine(String command, String requiredPart, CommandLineOption<?>... clo) {
		this.requiredPart = requiredPart;
		this.commandName = command;
		for (CommandLineOption<?> c : clo) {
			add(c);
		}


		add(new CommandLineOption<ArrayList<String>>("args",new ArrayList<String>(),true,
				"Read additional command-line options from the given file.  Can be used " +
		"multiple times.") {
			protected void apply(String arg) {
				this.get().add(arg);
				readArgsFromFile(arg);
			}
			protected String getType() {
				return "String";
			}
		});

		add(Util.debugKeysOption);
		add(Util.quietOption);
		add(Util.outputPathOption); 
		add(Util.outputFileOption); 
		add(Util.errorFileOption); 

	}


	protected void readArgsFromFile(String inFile) {
		try {
			Vector<String> args = new Vector<String>();
			Scanner in = new Scanner(new BufferedReader(new FileReader(inFile)));
			while (in.hasNextLine()) {
				String line = in.nextLine();
				if (!line.startsWith("#")) {
					for (String s : new IterableIterator<String>(new Scanner(line))) {
						args.add(s);
					}
				}
			}
			String argsArray[] = new String[args.size()];
			args.copyInto(argsArray);
			int end = this.applyHelper(argsArray, 0);
			if (end < argsArray.length) {
				Util.fail("Bad arg in file " + inFile + ": " + argsArray[end]);
			}
		} catch (IOException e) {
			Util.fail(e);
		}

	}

	public void usage() {
		Util.error("\n");
		Util.error("Usage\n-----");
		Util.error("    " + this.commandName + "  <options>  " + this.requiredPart + "\n");
		Util.error("Standard Options");
		Util.error("----------------");
		for (String c : usageInfo) {
			Util.error(c);
		}
		Util.error("");
	}

	public void addPostProcessor(Runnable r) {
		this.postProcess.add(r);
	}

	private void postProcess() {
		for (Runnable r : this.postProcess) {
			r.run();
		}
	}

	public int apply(String args[]) {
		int n = applyHelper(args, 0);

		String s = "";
		for (int i = n; i < args.length; i++) {
			s += args[i] + " ";
		}
		javaArgs.set(s);

		Option.dumpOptions();
		return n;
	}

	private int applyHelper(String args[], int firstArg) { 
		Vector<CommandLineOption<?>> processed = new Vector<CommandLineOption<?>>();

		while (firstArg < args.length) {
			String flag = args[firstArg];
			String arg = null;
			int equals = flag.indexOf('=');
			if (equals > -1) {
				arg = flag.substring(equals+1);
				flag = flag.substring(0,equals);
			}

			if (flag.startsWith("-")) {
				boolean found = false;
				String flagNoDash = flag.substring(1);
				int n = flags.size();
				// don't use iterator, so we can add more options as we process...
				for (int i = 0; i < n; i++) {
					CommandLineOption<?> clo = flags.get(i);
					if (clo.getId().equals(flagNoDash)) {
						found = true;
						CommandLineOption failure = constraints.findOutOfOrder(processed, clo);
						if (failure != null) {
							Util.fail("Option -%s cannot appear after -%s", clo.getId(), failure.getId());
						}
						processed.add(clo);
						clo.checkAndApply(arg);
					} 
				}
				if (!found) {
					Util.fail("Unrecognized Option: %s.", flag);					
				}
			} else {
				break;
			}
			firstArg++;
		}

		return firstArg;
	}

	public void addOrderConstraint(CommandLineOption<?> before, CommandLineOption<?> o) {
		constraints.addConstraint(before, o);
	}

	private static enum Nums { ONE, TWO, THREE };

	public static void main(String s[]) {
		CommandLineOption<?>[] os = new CommandLineOption<?>[] {
				makeBoolean("f1", false, "set f1"),
				makeBoolean("f2", true,  "set f2"),
				makeInteger("i1", 32, "set i1"),
				makeString("s1", "Cow", "set s1"),
				makeStringList("l1", "add to l"),
				makeEnumChoice("num", Nums.ONE, "moo", Nums.class)
		};
		CommandLine cl = new CommandLine("mpoo", "file1 file2", os);
		cl.usage();
		Option.dumpOptions();
		cl.apply(new String[] { "-f1", "-l=moo", "-l=moo2", "-num=TWO"} );
		cl.apply(new String[] { "-f1", "-l=moo", "-l=moo2", "-num=THREE"} );
		cl.apply(new String[] { "-f1", "-l=moo", "-l=moo2", "-num=ONE"} );
		Option.dumpOptions();
	}

}



