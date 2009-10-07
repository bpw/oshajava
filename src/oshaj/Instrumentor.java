package oshaj;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class Instrumentor implements ClassFileTransformer {
	
	public static void premain(String agentArgs, Instrumentation inst) {
		// Register the instrumentor with the jvm as a class file transformer.
		inst.addTransformer(new Instrumentor());
		// Add the libraries that our instrumentation hooks will call to the classpath.
//		inst.appendToSystemClassLoaderSearch(jarFileForRunTimeHooksEtc);
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> targetClass,
			ProtectionDomain protectionDomain, byte[] classFileBuffer)
			throws IllegalClassFormatException {
		// Use ASM to insert hooks for all the sorts of accesses, acquire, release, maybe fork, join, volatiles, etc.
		return classFileBuffer;
	}

}
