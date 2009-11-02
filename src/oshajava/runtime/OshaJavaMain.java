package oshajava.runtime;

import java.lang.reflect.Method;

import oshajava.instrument.InstrumentingClassLoader;
import oshajava.support.acme.util.Util;

public class OshaJavaMain {
	
	public static void main(final String[] args) {
		final InstrumentingClassLoader icl = new InstrumentingClassLoader(ClassLoader.getSystemClassLoader());
		final Class<?> cl;
		final Method main;
		try {
			cl = icl.loadClass(args[0]);
			main = cl.getMethod("main", String[].class);
		} catch (Throwable e) {
			Util.fail(e);
			return;
		}
		final String[] appArgs = new String[args.length - 1];
		System.arraycopy(args, 1, appArgs, 0, args.length - 1);
		// TODO shutdown hooks
		final Thread app = new Thread("Application main") {
			public void run() {
				try {
					setContextClassLoader(icl);
					main.invoke(cl, (Object)appArgs);
				} catch (Throwable e) {
					Util.fail(e);
				}
			}
		};
		app.start();
	}

}
