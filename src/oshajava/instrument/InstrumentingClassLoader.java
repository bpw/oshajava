package oshajava.instrument;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.SimpleRemapper;

import acme.util.Util;

public class InstrumentingClassLoader extends ClassLoader {
	
	public InstrumentingClassLoader(ClassLoader parent) {
		super(parent);
		Util.log("ICL consructed");
	}
	
	protected static final String ALT_JDK_PKG = "__osha_ins";

	@Override
	protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
		if (name.startsWith(ALT_JDK_PKG + ".")) {
			Util.log("ICL: " + name);
			// load from java.*
			ClassReader cr;
			try {
				cr = new ClassReader(name.substring((ALT_JDK_PKG + ".").length()));
			} catch (IOException e) {
				ClassNotFoundException c = new ClassNotFoundException();
				c.initCause(e);
				throw c;
			}
			ClassWriter cw = new ClassWriter(cr, 0);
			Remapper r = new SimpleRemapper(InstrumentationAgent.toCopy);
			cr.accept(new RemappingClassAdapter(cw, r), 0);
			byte[] bytecode = cw.toByteArray();
			return defineClass(name, bytecode, 0, bytecode.length);
		} else {
			throw new ClassNotFoundException();
		}
	}
	
}
