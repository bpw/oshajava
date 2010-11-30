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

package oshajava.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Stack;

import oshajava.support.acme.util.Assert;

public class XMLWriter {
	
	private final Writer xml;
	private final Stack<String> scope = new Stack<String>();
	private static final String INDENT = "  ";
	
	public XMLWriter(final String file) throws IOException {
		 xml = new PrintWriter(new File(file));
	}
	
	private void indent() throws IOException {
		for (int i = 0; i < scope.size(); i++) {
			xml.write(INDENT);
		}
	}
	
	private void tag(final String tag, final Object... attributes) throws IOException {
		Assert.assertTrue(attributes.length % 2 == 0, "Unbalanced key/value in attributes.");
		xml.write(tag);
		boolean key = true;
		for (final Object s : attributes) {
			if (key) {
				xml.write(' ');
				xml.write(s.toString());
				xml.write('=');
			} else {
				xml.write('"');
				xml.write(filterForXml(s.toString()));
				xml.write('"');
			}
			key = !key;
		}
	}
	
	public void singleton(final String tag, final Object... attributes) throws IOException {
		Assert.assertTrue(attributes.length % 2 == 0, "Unbalanced key/value in attributes.");
		indent();
		xml.write('<');
		tag(tag, attributes);
		xml.write(" />\n");
	}

	public void start(final String tag, final Object... attributes) throws IOException {
		Assert.assertTrue(attributes.length % 2 == 0, "Unbalanced key/value in attributes.");
		indent();
		xml.write('<');
		tag(tag, attributes);
		xml.write(">\n");
		scope.push(tag);
	}

	public void end(final String tag) throws IOException {
		Assert.assertTrue(!scope.isEmpty(), "No open start tag to match end tag </" + tag + ">.");
		final String startTag = scope.pop();
		Assert.assertTrue(tag.equals(startTag), "End tag </" + tag + "> does not match start tag <" + startTag + ">.");
		indent();
		xml.write("</" + tag + ">\n");
	}
	
	public void close() throws IOException {
		xml.close();
		if (!scope.isEmpty()) {
			Assert.warn("Missing end tags: " + scope + ".  Closed anyway.");
		}
	}

	private static String filterForXml(String s) {
		return s.replace('<', '{').replace('>', '}');
	}


}
