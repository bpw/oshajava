package oshajava.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import oshajava.support.acme.util.Util;

public class ColdStorage {
	
	public static void dump(String file, Serializable o) {
		try {
			final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(new File(file)));
			out.writeObject(o);
		} catch (final IOException e) {
			Util.fail(e);
		}
	}
	
	public static Object load(String file) {
		try {
			final ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(file)));
			return in.readObject();
		} catch (final IOException e) {
			Util.fail(e);
		} catch (ClassNotFoundException e) {
			Util.fail(e);
		}
		return null;
	}
	
}
