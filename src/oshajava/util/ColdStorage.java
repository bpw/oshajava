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

/**
 * Utilities for serializing and deserializing objects from files and streams.
 * @author bpw
 *
 */
public class ColdStorage {
	
	/**
	 * Dump one object to one file.
	 * @param file
	 * @param o
	 * @throws IOException
	 */
	public static void dump(String file, Serializable o) throws IOException {
		final FileOutputStream out = new FileOutputStream(new File(file));
		dump(out, o);
		out.close();
	}
	
	/**
	 * Dump an object to a stream;
	 * @param stream
	 * @param o
	 * @throws IOException
	 */
	public static void dump(OutputStream stream, Serializable o) throws IOException {
		final ObjectOutputStream out = new ObjectOutputStream(stream);
		out.writeObject(o);
		out.close();
	}
	
	/**
	 * Load one object from one file.
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(String file) throws IOException, ClassNotFoundException {
		final FileInputStream in = new FileInputStream(new File(file));
		final Object o = load(in);
		in.close();
		return o;
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
		final Object o = in.readObject();
		in.close();
		return o;
	}
	
}
