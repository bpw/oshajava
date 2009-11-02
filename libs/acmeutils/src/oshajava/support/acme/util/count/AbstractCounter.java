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

package oshajava.support.acme.util.count;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Vector;

import oshajava.support.acme.util.io.XMLWriter;



public abstract class AbstractCounter implements Comparable<AbstractCounter> {

	static private final Vector<AbstractCounter> all = new Vector<AbstractCounter>();
	
	protected final String name;
	protected final String group;
	private int createOrder = 0;
	
	public AbstractCounter(String group, String name) {
		this.name = name;
		this.group = group;
		this.createOrder = all.size();
		all.add(this);
	}
	
	public AbstractCounter(String name) {
		this(null, name);
	}
	
	public abstract String get();
	
	private String fullRep() {
		return (group == null ? name : group + ": " + name);
	}
	
	public String toString() {
		return String.format("%-50s : %5s", fullRep(), get());
	}

	@Deprecated
	public String toXML() {
		return String.format("<counter name=   %-50s> %5s </counter>", String.format("\"%s\"", fullRep()), get());
	}
	 
	public void printXML(XMLWriter out) {
		out.printInsideScopeWithFixedWidths("counter", "name", String.format("\"%s\"", fullRep()), -60, "value", get(), -5);
	}
	
	public static void printCounters(PrintStream out) {
		java.util.Collections.sort(all);
		String previousGroup = null;
		for (AbstractCounter c : all) {
			String result = "";
			if (previousGroup != c.group) result += "\n";
			result += "          ";
			previousGroup = c.group;
			result += "  ";
			result += c.toString();
			out.println(result);
		}
	}

	public static String allToString() {
		ByteArrayOutputStream sout = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(sout);
		printCounters(out);
		out.close();
		return sout.toString() + "         ";
	}

	public static synchronized void printAllXML(XMLWriter out) {
		java.util.Collections.sort(all);
		out.push("counters");
		String previousGroup = null;
		for (AbstractCounter c : all) {
			if (previousGroup != c.group) out.blank();
			previousGroup = c.group;
			c.printXML(out);
		}
		out.pop();
	}
	
	@Deprecated
	public static synchronized String countersToXML() {
		java.util.Collections.sort(all);
		String result = "<counters>";
		String previousGroup = null;
		for (AbstractCounter c : all) {
			if (previousGroup != c.group) result += "\n";
			previousGroup = c.group;
			result += "  ";
			result += c.toXML();
			result += "\n";
		}
		result += "</counters>";
		return result;
	}

	public int compareTo(AbstractCounter other) {
		if (group == null && other.group == null) {
			return createOrder - other.createOrder;
		} if (other.group == null) {
			return 1;
		} if (group == null) {
			return -1;
		} else if (group.compareTo(other.group) == 0){
			return createOrder - other.createOrder;
		} else {
			return group.compareTo(other.group);
		}
	}

	
}
