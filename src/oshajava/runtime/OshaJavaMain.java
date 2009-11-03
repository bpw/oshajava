package oshajava.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import oshajava.instrument.InstrumentationAgent;
import oshajava.instrument.InstrumentingClassLoader;
import oshajava.support.acme.util.Util;

public class OshaJavaMain {
	
	public static void main(final String[] args) {
		InstrumentationAgent.setMainClass(args[0]);
		// TODO shutdown hooks
		final ThreadGroup appGroup = new ThreadGroup("application thread group root");
		InstrumentationAgent.setAppThreadGroupRoot(appGroup);
		final Thread app = new Thread(appGroup, "application main") {
			public void run() {
				try {
					final Class<?> cl;
					final Method main;
					try {
						cl = ClassLoader.getSystemClassLoader().loadClass(args[0]);
						main = cl.getMethod("main", String[].class);
					} catch (Throwable e) {
						Util.fail(e);
						return;
					}
					final String[] appArgs = new String[args.length - 1];
					System.arraycopy(args, 1, appArgs, 0, args.length - 1);
					main.invoke(cl, (Object)appArgs);
				} catch (InvocationTargetException e) {
					Util.fail(e.getCause());
				} catch (Throwable e) {
					Util.fail(e);
				}
			}
		};
		Util.logf("Starting %s", args[0]);
		app.start();
		try {
			app.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			Util.fail(e);
		}
	}

}
