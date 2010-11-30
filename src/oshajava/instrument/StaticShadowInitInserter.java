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

import java.util.List;

import oshajava.spec.names.FieldDescriptor;
import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.Type;
import oshajava.support.org.objectweb.asm.commons.AdviceAdapter;


public class StaticShadowInitInserter extends AdviceAdapter {

	private final Type classType;
	private final List<FieldDescriptor> inlineInitFields;
	public StaticShadowInitInserter(MethodVisitor mv, int access, String name, String desc, Type classType, List<FieldDescriptor> inlineInitFields) {
		super(mv, access, name, desc);
		this.classType = classType;
		this.inlineInitFields = inlineInitFields;
	}

    private int varCurrentThread;
    private int maxStack;
    private int maxLocals;

	@Override
	public void onMethodEnter() {
	    // Enter hook.
	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_ENTER_CLINIT);
	    varCurrentThread = super.newLocal(ClassInstrumentor.THREAD_STATE_TYPE);
	    super.storeLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	    
	    // Initialization.
	    if (inlineInitFields == null) {
    		super.invokeStatic(classType, ClassInstrumentor.STATIC_SHADOW_INIT_METHOD);
    	} else if (!inlineInitFields.isEmpty()) {
    	    // inlineInitFields indicates that this is an interface, and
    	    // we should initialize these fields directly in the clinit
    	    // instead of invoking the initer.
    	    int varCurrentState = super.newLocal(ClassInstrumentor.STATE_TYPE);
    	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_CURRENT_STATE);
    	    super.storeLocal(varCurrentState);
    	    for (FieldDescriptor field : inlineInitFields) {
			    // Shadow field.
				super.loadLocal(varCurrentState);
				super.putStatic(classType, field.getFieldName() + ClassInstrumentor.SHADOW_FIELD_SUFFIX, ClassInstrumentor.STATE_TYPE);
			}
    	}
	}
	
	private void exitHook() {
	    super.loadLocal(varCurrentThread, ClassInstrumentor.THREAD_STATE_TYPE);
	    super.invokeStatic(ClassInstrumentor.RUNTIME_MONITOR_TYPE, ClassInstrumentor.HOOK_EXIT);
	}
	
	@Override
	public void visitEnd() {
	    exitHook();
	    super.visitMaxs(maxStack+1, maxLocals+1);
	    super.visitEnd();
	}
	
	@Override
	public void visitInsn(int opcode) {
	    switch (opcode) {
        case IRETURN:
		case LRETURN:
		case FRETURN:
		case DRETURN:
		case ARETURN:
		case RETURN:
		    exitHook();
	    default:
			super.visitInsn(opcode);
			break;
		}
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
	    this.maxStack = maxStack;
	    this.maxLocals = maxLocals;
	}

}
