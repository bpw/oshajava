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
