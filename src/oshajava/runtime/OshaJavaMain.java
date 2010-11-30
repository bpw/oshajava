/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import oshajava.instrument.Agent;
import oshajava.instrument.Rewriter;
import oshajava.runtime.exceptions.OshaRuntimeException;
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
		final ThreadGroup appGroup = new ThreadGroup("application thread group root");
		final ClassLoader loader = Agent.getMappingLoader();
		final Thread app = new Thread(appGroup, "application main") {
			public void run() {
				final SequentialTimer mainTimer = new SequentialTimer("Main time");
				mainTimer.start();
				
				try {
					final Class<?> cl;
					final Method main;
					try {
						cl = loader.loadClass(Rewriter.map(args[0]));
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
				} catch (OshaRuntimeException e) {
					Assert.fail(e);
				} catch (Throwable e) {
					Assert.warn(e.getCause());
					Assert.fail(e);
				} finally {
					mainTimer.stop();
				}
			}
		};
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				Agent.stopInstrumentation();
				RuntimeMonitor.fini(mainClass);
			}
		});
		app.setContextClassLoader(loader);
		Agent.initializeProgram(mainClass, appGroup);
		Debug.debugf("main", "Starting %s", mainClass);
		app.start();
		try {
			app.join();
		} catch (InterruptedException e) {
			Assert.panic(e);
		}
	}

}
