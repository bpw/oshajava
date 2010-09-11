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

import javax.tools.FileObject;

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
	public static void store(String path, Serializable o) throws IOException {
		ObjectFile f = new ObjectFile(path);
		final FileOutputStream out = new FileOutputStream(f);
		store(out, o);
		out.close();
	}
	
	/**
	 * Dump one object to a file by URI.
	 * @param uri
	 * @param o
	 * @throws IOException
	 */
	public static void store(URI uri, Serializable o) throws IOException {
		ObjectFile f = new ObjectFile(uri);
		f.createNewFile();
		final FileOutputStream out = new FileOutputStream(f);
		store(out, o);
		out.close();
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
	 * Dump an object to a FileObject;
	 * @param stream
	 * @param o
	 * @throws IOException
	 */
	public static void store(FileObject f, Serializable o) throws IOException {
		OutputStream out = f.openOutputStream();
		store(out, o);
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
	 * Load one object from one file by URI.
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(URI file) throws IOException, ClassNotFoundException {
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
		return in.readObject();
	}
	
	/**
	 * Load an object from a FileObject.
	 * @param stream
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Object load(FileObject f) throws IOException, ClassNotFoundException {
		final InputStream in = f.openInputStream();
		final Object o = load(in);
		in.close();
		return o;
	}
	
	static class ObjectFile extends File {

		public ObjectFile(String path) throws IOException {
			super(path);
			create();
		}
		
		public ObjectFile(URI uri) throws IOException {
			super(uri);
			create();
		}
		
		public void create() throws IOException {
			File parent = new File(getParent());
			parent.mkdirs();
			createNewFile();
		}
	}
	
}
