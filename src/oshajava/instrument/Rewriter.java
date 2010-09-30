package oshajava.instrument;

import oshajava.spec.names.TypeDescriptor;
import oshajava.support.acme.util.Assert;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.commons.Remapper;
import oshajava.support.org.objectweb.asm.commons.RemappingClassAdapter;
import oshajava.support.org.objectweb.asm.commons.RemappingMethodAdapter;

public class Rewriter extends RemappingClassAdapter {
	
	/**
	 * Prefix to use when mapping classes.
	 */
	public static final String PREFIX = "__osha__";
	
	public static boolean shouldMap(String typeName) {
		return Agent.remapOption.get() && !typeName.startsWith(PREFIX) && Filter.shouldInstrument(TypeDescriptor.ofClass(typeName));
	}

	public static String map(String typeName) {
		if (shouldMap(typeName)) {
			return PREFIX + typeName;
		}
		return typeName;
	}
	
	public static String unmap(String mappedName) {
		if (mappedName.startsWith(PREFIX)) return mappedName.substring(PREFIX.length());
		return mappedName;
	}
	
	protected static boolean isMapped(String name) {
		return name.startsWith(PREFIX);
	}

	/**
	 * For remapping things like java.util.* to __osha__java.util.*.
	 */
	protected static final Remapper mapper = new Remapper() {
		@Override
		public String map(String typeName) {
			return Rewriter.map(typeName);
		}
	};

	public Rewriter(ClassVisitor cv) {
		super(cv, mapper);
	}

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    	Assert.assertTrue(shouldMap(className));
    	super.visit(version, access, name, signature, superName, interfaces);
    }

    protected MethodVisitor createRemappingMethodAdapter(
        int access,
        String newDesc,
        MethodVisitor mv)
    {
        return new RemappingMethodAdapter(access, newDesc, mv, remapper) {
        	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        		mv.visitFieldInsn(opcode, remapper.mapType(owner), name, shouldMap(owner) ? remapper.mapDesc(desc) : desc);
        	}

        	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        		super.visitMethodInsn(opcode, remapper.mapType(owner), name, shouldMap(owner) ? remapper.mapMethodDesc(desc) : desc);
        	}
        	// FIXME might try to store a __osha__Foo into a field of type Foo or pass an __osha__Bar to method taking bar...  We need to follow all uses
        	// to really see... completely impractical.  This basically dooms completely robust remapping.  Best we can do now is make the right choice of 
        	// boundaries, or give oshajava a static copy of everything it needs.
        };
    }

}
