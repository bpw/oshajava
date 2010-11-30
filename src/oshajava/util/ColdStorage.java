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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;

/**
 * Utilities for serializing and deserializing objects from files and streams.
 * @author bpw
 *
 */
public class ColdStorage {
	
	/**
	 * Dump one object to one file.
	 * @param path
	 * @param o
	 * @throws IOException
	 */
	public static void store(File path, Serializable o) throws IOException {
		if (!path.exists()) {
			File parent = new File(path.getParent());
			parent.mkdirs();
			path.createNewFile();
		}
		final FileOutputStream out = new FileOutputStream(path, false);
		store(out, o);
		out.close();
	}
	
	/**
	 * Dump one object to one file.
	 * @param path
	 * @param o
	 * @throws IOException
	 */
	public static void store(String path, Serializable o) throws IOException {
		store(new File(path), o);
	}
	
	/**
	 * Dump one object to a file by URI.
	 * @param uri
	 * @param o
	 * @throws IOException
	 */
	public static void store(URI uri, Serializable o) throws IOException {
		store(new File(uri.getPath()).getAbsoluteFile(), o);
	}
	
	/**
	 * Dump an object to a stream;
	 * @param stream
	 * @param o
	 * @throws IOException
	 */
	public static void store(OutputStream stream, Serializable o) throws IOException {
		final ObjectOutputStream out = new ObjectOutputStream(stream);
		out.writeObject(o);
	}
	
	/**
	 * Load one object from one file.
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(File file) throws IOException, ClassNotFoundException {
		final FileInputStream in = new FileInputStream(file);
		final Object o = load(in);
		in.close();
		return o;
	}
	
	/**
	 * Load one object from one file.
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(String file) throws IOException, ClassNotFoundException {
		return load(new File(file));
	}
	
	/**
	 * Load one object from one file by URI.
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(URI uri) throws IOException, ClassNotFoundException {
		return load(new File(uri.getPath()).getAbsoluteFile());
	}
	
	/**
	 * Load an object from a stream.
	 * @param stream
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(InputStream stream) throws IOException, ClassNotFoundException {
		final ObjectInputStream in = new ObjectInputStream(stream);
		return in.readObject();
	}
	
}
