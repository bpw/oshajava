package oshajava.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public class GraphMLWriter {
	private final Writer graphml;
	
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
		 graphml.write("<key id=\"fnname\" for=\"node\" attr.name=\"fnname\" attr.type=\"string\"/>\n");
		 graphml.write("<key id=\"file\" for=\"node\" attr.name=\"file\" attr.type=\"string\"/>\n");
		 graphml.write("<key id=\"kind\" for=\"edge\" attr.name=\"kind\" attr.type=\"string\"/>\n\n\n");
		 graphml.write("<key id=\"weight\" for=\"edge\" attr.name=\"weight\" attr.type=\"double\"/>\n");
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
		graphml.write("\t<data key=\"kind\">" + filterForXml(kind) + "</data>\n");
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
