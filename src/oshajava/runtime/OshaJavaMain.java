package oshajava.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import oshajava.instrument.InstrumentationAgent;
import oshajava.support.acme.util.Assert;
import oshajava.support.acme.util.Debug;
import oshajava.util.count.SequentialTimer;

public class OshaJavaMain {
	
	public static void main(final String[] args) {
			if (Config.helpOption.get()) {
			Config.cl.usage();
			return;
		}
		if (args.length == 0) {
			Assert.fail("No main class given.");
		}
		final String mainClass = args[0];
		InstrumentationAgent.setMainClass(mainClass);
		final ThreadGroup appGroup = new ThreadGroup("application thread group root");
		InstrumentationAgent.setAppThreadGroupRoot(appGroup);
		final Thread app = new Thread(appGroup, "application main") {
			public void run() {
				final SequentialTimer mainTimer = new SequentialTimer("Main time");
				mainTimer.start();
				
				try {
					final Class<?> cl;
					final Method main;
					try {
						cl = ClassLoader.getSystemClassLoader().loadClass(args[0]);
						main = cl.getMethod("main", String[].class);
					} catch (Throwable e) {
						Assert.fail(e);
						return;
					}
					final String[] appArgs = new String[args.length - 1];
					System.arraycopy(args, 1, appArgs, 0, args.length - 1);
					main.invoke(cl, (Object)appArgs);
				} catch (InvocationTargetException e) {
					Assert.fail(e.getCause());
				} catch (Throwable e) {
					Assert.fail(e);
				} finally {
					mainTimer.stop();
				}
			}
		};
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				InstrumentationAgent.stopInstrumentation();
				RuntimeMonitor.fini(mainClass);
			}
		});
		Debug.debugf("main", "Starting %s", mainClass);
		app.start();
		try {
			app.join();
		} catch (InterruptedException e) {
			Assert.fail(e);
		}
	}

}
