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
