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

package oshajava.support.acme.util.io;

import java.io.IOException;
import java.io.Writer;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class HTMLWriter extends Writer {

	protected final Writer out;

	public HTMLWriter(Writer out) {
		this.out = out;
	}
	

	@Override
	public void close() throws IOException {
		out.close();
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		final String text = new String(cbuf, off, len);
		final String process = process(text);
		out.write(process);
	}

	public void unescapedWrite(String s) throws IOException {
		out.write(s);
	}
	
	// from http://www.javapractices.com/topic/TopicAction.do?Id=96
	// why did they not use a switch???????
	protected String process(String text) {
		final StringBuilder result = new StringBuilder();
		final StringCharacterIterator iterator = new StringCharacterIterator(text);
		char character =  iterator.current();
		while (character != CharacterIterator.DONE ){
			if (character == '<') {
				result.append("&lt;");
			}
			else if (character == '>') {
				result.append("&gt;");
			}
			else if (character == '&') {
				result.append("&amp;");
			}
			else if (character == '\"') {
				result.append("&quot;");
			}
			else if (character == '\t') {
				addCharEntity(9, result);
			}
			else if (character == '!') {
				addCharEntity(33, result);
			}
			else if (character == '#') {
				addCharEntity(35, result);
			}
			else if (character == '$') {
				addCharEntity(36, result);
			}
			else if (character == '%') {
				addCharEntity(37, result);
			}
			else if (character == '\'') {
				addCharEntity(39, result);
			}
			else if (character == '(') {
				addCharEntity(40, result);
			}
			else if (character == ')') {
				addCharEntity(41, result);
			}
			else if (character == '*') {
				addCharEntity(42, result);
			}
			else if (character == '+') {
				addCharEntity(43, result);
			}
			else if (character == ',') {
				addCharEntity(44, result);
			}
			else if (character == '-') {
				addCharEntity(45, result);
			}
			else if (character == '.') {
				addCharEntity(46, result);
			}
			else if (character == '/') {
				addCharEntity(47, result);
			}
			else if (character == ':') {
				addCharEntity(58, result);
			}
			else if (character == ';') {
				addCharEntity(59, result);
			}
			else if (character == '=') {
				addCharEntity(61, result);
			}
			else if (character == '?') {
				addCharEntity(63, result);
			}
			else if (character == '@') {
				addCharEntity(64, result);
			}
			else if (character == '[') {
				addCharEntity(91, result);
			}
			else if (character == '\\') {
				addCharEntity(92, result);
			}
			else if (character == ']') {
				addCharEntity(93, result);
			}
			else if (character == '^') {
				addCharEntity(94, result);
			}
			else if (character == '_') {
				addCharEntity(95, result);
			}
			else if (character == '`') {
				addCharEntity(96, result);
			}
			else if (character == '{') {
				addCharEntity(123, result);
			}
			else if (character == '|') {
				addCharEntity(124, result);
			}
			else if (character == '}') {
				addCharEntity(125, result);
			}
			else if (character == '~') {
				addCharEntity(126, result);
			}
			else {
				//the char is not a special one
				//add it to the result as is
				result.append(character);
			}
			character = iterator.next();
		}
		return result.toString();
	}

	private void addCharEntity(Integer aIdx, StringBuilder aBuilder){
		String padding = "";
		if( aIdx <= 9 ){
			padding = "00";
		}
		else if( aIdx <= 99 ){
			padding = "0";
		}
		else {
			//no prefix
		}
		String number = padding + aIdx.toString();
		aBuilder.append("&#" + number + ";");
	}



}
