package oshajava.instrument;

public class InstrumentingClassLoader extends ClassLoader {
	
	protected static final String ALT_JDK_PREFIX = "javacopy";

	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve)
			throws ClassNotFoundException {
		if (name.startsWith(ALT_JDK_PREFIX)) {
			// load from java.*
		}
		return super.loadClass(name, resolve);
	}
	
	protected boolean loadedByMe(String name) {
		return null == findLoadedClass(name);
	}
	
}
