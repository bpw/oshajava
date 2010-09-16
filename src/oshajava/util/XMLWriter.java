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
