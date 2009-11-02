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

import oshajava.support.acme.util.Strings;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.io.XMLWriter;

public abstract class CommandLineOption<T> extends Option<T> {

	final protected boolean hasArg;
	final protected String usage;
	
	protected CommandLine container;

	public CommandLineOption(String id, T dV, boolean hasArg, String usage) {
		super(id, dV);
		this.hasArg = hasArg;
		this.usage = usage;
	}

	protected abstract void apply(String arg);

	public void checkAndApply(String arg) {
		if (hasArg && arg == null) {
			Util.fail("Command Line Option '%s' requires a value", id);
		} else if (!hasArg && arg != null) {
			Util.fail("Command Line Option '%s' does not take a value", id);
		} else {
			apply(arg);
		}
	}

	public boolean hasArg() {
		return hasArg;
	}

	protected String usage() {
		return usage;
	}

	protected String getType() {
		T t = this.get();
		return (t == null ? "NULL" : t.getClass().getSimpleName());
	}
	
	public String getUsage() {
		String args = "    -" + id;
		if (hasArg) {
			args += "={" + getType() + "}";
		}
		args = Strings.pad(args, 35, ' ');
		String prepend = Strings.pad("", 35, ' ');
		return String.format("%s%s\n", args, Strings.wordWrap(usage(), 80, "\n", "",prepend));
	}
	
	void setCommandLine(CommandLine cl) {
		if (this.container != null) {
			Util.fail("Command Line Option %s already contained in a command line.", this.id);
		}
		this.container = cl;
	}
	
	public CommandLine getCommandLine() {
		return container;
	}
}
