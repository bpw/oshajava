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

import java.util.ArrayList;

import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.io.XMLWriter;
import oshajava.support.acme.util.time.TimedStmt;



public class Option<T> {

	private static ArrayList<Option<?>> options = new ArrayList<Option<?>>(); 

	final protected String id;
	protected T val;
	final protected T defaultVal;

	final private String location;

	public Option(String id, T dV) {
		this.id = id;
		this.set(this.defaultVal = dV);
		options.add(this);

		StackTraceElement st[] = (new Throwable()).getStackTrace();
		int n = 0;
		while (st[n].getClassName().indexOf("acme.util.option.Option") > -1 || 	
				st[n].getClassName().indexOf("acme.util.option.CommandLine") > -1) {
			n++;
		}
		location = st[n].getClassName();
	}

	public void set(T val) {
		this.val = val;
	}

	public T get() {
		return val;
	}

	public String getId() {
		return this.id;
	}

	protected String rep() {
		return val == null ? "null" : val.toString();
	}

	@Override
	public String toString() {
		String res = String.format("%-34s %-20s = %-20s", "(" + location + ")", id, val);
		return res;
	}

	public static void printAllXML(XMLWriter xml) {
		xml.push("options");
		for (Option o : options) {
			xml.printInsideScopeWithFixedWidths("option", "name", o.id, -34, "value", o.rep(), -20);
		}
		xml.pop();
	}

	@Deprecated
	protected String toXML() {
		String res = String.format("<option name=\"%s\">%s</option>", id, rep(), id);
		return res;
	}

	public static void dumpOptions() {
		try {
			new TimedStmt("OPTIONS:") {
				@Override
				public void run() {
					for (Option o : options) {
						Util.log(o.toString());
					}
				}
			}.eval();
		} catch (Exception e) {
			Util.panic(e);
		}
	}

	@Deprecated
	public static String optionsToXML() {
		String result = "<options>\n";
		for (Option o : options) {
			result += "   ";
			result += o.toXML();
			result += "\n";
		}
		result += "</options>";
		return result;
	}
}
