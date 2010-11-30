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

public class GraphMLWriter {
	private final Writer graphml;
	
	private static final String KIND_KEY = "kind", STACK_KEY = "stack";
	
	public GraphMLWriter(final String file) throws IOException {
		 graphml = new PrintWriter(new File(file));
		 // print boilerplate.
		 graphml.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		 graphml.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
		 graphml.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
		 graphml.write("xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n");
		 graphml.write(" http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
		 graphml.write("<graph id=\"G\" edgedefault=\"directed\">\n");
		 graphml.write("<!-- data schema -->\n\n");
		 graphml.write("<key id=\"" + STACK_KEY + "\" for=\"node\" attr.name=\"" + STACK_KEY + "\" attr.type=\"int\"/>\n");
		 graphml.write("<key id=\"" + KIND_KEY + "\" for=\"edge\" attr.name=\"" + KIND_KEY + "\" attr.type=\"string\"/>\n\n\n");
	}
	
	public void writeNode(final String id, final String name, final String file) throws IOException {
		graphml.write("<node id=\"" + filterForXml(id) + "\">\n");
		graphml.write("\t<data key=\"fnname\">" + filterForXml(name) + "</data>\n");
		graphml.write("\t<data key=\"file\">" + filterForXml(file) + "</data>\n");
		graphml.write("</node>\n");
	}
	
	public void writeEdge(final String sourceID, final String destID, final String kind) throws IOException {
		writeEdge(sourceID, destID, kind, 1);
	}
	
	public void writeEdge(final String sourceID, final String destID, final String kind, final int weight) throws IOException {
		graphml.write("<edge source=\"" + filterForXml(sourceID) + "\" target=\"" +  filterForXml(destID) + "\">\n");
		graphml.write("\t<data key=\"" + KIND_KEY + "\">" + filterForXml(kind) + "</data>\n");
		graphml.write("\t<data key=\"weight\">" + weight + "</data>\n");
		graphml.write("</edge>\n");
	}
	
	public void close() throws IOException {
		graphml.write("</graph>\n</graphml>\n");
		graphml.close();
	}
	
	private static String filterForXml(String s) {
		return s.replace('<', '{').replace('>', '}');
	}


}
