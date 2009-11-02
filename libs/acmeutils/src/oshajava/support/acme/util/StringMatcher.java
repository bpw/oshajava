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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

public class StringMatcher {
	private HashMap<String,StringMatchResult> cache = new HashMap<String,StringMatchResult>();
	
	static class Entry {
		final Pattern pattern;
		final boolean positive;
		public Entry(String ps) {
			this.pattern = Pattern.compile(ps.substring(1));
			this.positive = ps.startsWith("+");
		}
		public StringMatchResult match(String s) {
			boolean m = pattern.matcher(s).matches();
			if (m && positive) return StringMatchResult.ACCEPT;
			if (m && !positive) return StringMatchResult.REJECT;
			return StringMatchResult.NOTHING;
		}
		public String toString() {
			return (positive?"+":"-")+pattern;
		}
	}
	
	private ArrayList<Entry> entries = new ArrayList<Entry>();
	private StringMatchResult defaultResult = StringMatchResult.NOTHING;
	
	public StringMatcher(String... pats) {
		for (String s: pats) {
			entries.add(new Entry(s));
		}
	}
	
	public StringMatcher(Iterator<String> pats) {
		while (pats.hasNext()) {
			entries.add(new Entry(pats.next()));
		}
	}

	public StringMatcher(StringMatchResult defaultResult, String... pats) {
		this(pats);
		this.defaultResult = defaultResult;
	}
	
	public StringMatcher(StringMatchResult defaultResult, Iterator<String> pats) {
		this(pats);
		this.defaultResult = defaultResult;		
	}

	
	public void add(String s) {
		entries.add(new Entry(s));
	}

	public void addFirst(String s) {
		entries.add(0,new Entry(s));
	}
	
	public void addNFromEnd(int defaultLen, String s) {
		entries.add(entries.size() - defaultLen, new Entry(s));
	}
	
	public String toString() {
		StringBuffer res = new StringBuffer("[");
		for (Entry p: entries) {
			res.append("\"" + p.toString().replaceAll("<", "&lt;").replaceAll(">", "&gt;") + "\", ");
		}
		res.append("default=" + this.defaultResult);
		res.append("]");
		return res.toString();
		
	}
	
	public StringMatchResult test(String s) {
		StringMatchResult r = cache.get(s);
		if (r != null) {
			return r;
		}
		 r = this.testNoCache(s);
		cache.put(s,r);
		return r;
	}
	
	private StringMatchResult testNoCache(String s) {
		for (Entry p: entries) {
			switch(p.match(s)) {
			case ACCEPT: return StringMatchResult.ACCEPT;
			case REJECT: return StringMatchResult.REJECT;
			}
		}
		return defaultResult;
	}

}
