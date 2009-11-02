package oshajava.instrument;

import java.util.concurrent.ConcurrentHashMap;

import oshajava.support.acme.util.Util;


public class InstrumentingClassLoader extends ClassLoader {
	
	private static final ConcurrentHashMap<String,Object> initiatedClasses = new ConcurrentHashMap<String,Object>();
	
	protected static boolean initiated(String cl) {
		return initiatedClasses.containsKey(cl);
	}
	
	public InstrumentingClassLoader(ClassLoader parent) {
		super(parent);
//		Util.log("ICL constructed");
	}
	
	protected static final String ALT_JDK_PKG = "__osha_ins";

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		Util.logf("ICL initiating %s", name);
		initiatedClasses.put(name, this);
		Class<?> cl = super.loadClass(name, resolve);
		Util.logf("ICL done %s", name);
		return cl;
		// problem is that when delegating to parent, parent doesn't call loadClass on all other classes it might
		// load in the process of loading this one. So...
	}
	
//	@Override
//	protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
//		Util.log("ICL: load " + name);
//		if (name.startsWith(ALT_JDK_PKG + "/")) {
//			// load from java.*
//			ClassReader cr;
//			try {
//				cr = new ClassReader(name.substring((ALT_JDK_PKG + "/").length()));
//			} catch (IOException e) {
//				ClassNotFoundException c = new ClassNotFoundException(name);
//				c.initCause(e);
//				throw c;
//			}
//			ClassWriter cw = new ClassWriter(cr, 0);
//			Remapper r = new SimpleRemapper(InstrumentationAgent.toCopy);
//			cr.accept(new RemappingClassAdapter(cw, r), 0);
////			cr.accept(cw, 0);
//			byte[] bytecode = cw.toByteArray();
//			return defineClass(name, bytecode, 0, bytecode.length);
//		} else {
//			throw new ClassNotFoundException();
//		}
//	}
	
}
