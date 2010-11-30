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

import oshajava.support.org.objectweb.asm.ClassAdapter;
import oshajava.support.org.objectweb.asm.ClassVisitor;
import oshajava.support.org.objectweb.asm.MethodAdapter;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Opcodes;

public class RemoveJava6Adapter extends ClassAdapter implements ClassVisitor {

	public RemoveJava6Adapter(ClassVisitor cv) {
		super(cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		if (version == Opcodes.V1_6 || version == Opcodes.V1_7) {
			super.visit(Opcodes.V1_5, access, name, signature, superName, interfaces);
		} else {
		    //TODO: This is a simple hack to allow LDC instructions to load classes.
		    // (This allows static synchronized methods to work without more
		    // work on the instrumentation end.) It could cause incompatibilities --
		    // we can change it back later.
			super.visit(Opcodes.V1_5, access, name, signature, superName, interfaces);
		}
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		return new MethodAdapter(super.visitMethod(access, name, desc, signature, exceptions)) {
			   public void visitFrame(final int type, final int nLocal, final Object[] local,
				        final int nStack, final Object[] stack) { }
		};
	}

}
