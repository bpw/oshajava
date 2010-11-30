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

/**
 * Provides an adapter that sorts well-nested exception handlers. This allows
 * ASM instrumentation code to generate arbitrary new try/catch blocks.
 * Without handler sorting, "outer" handlers could surprisingly override
 * "inner" handlers, causing unexpected results.
 *
 * Results are undefined for non-nested (i.e., overlapping) handlers.
 */
package oshajava.instrument;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import oshajava.support.org.objectweb.asm.MethodVisitor;
import oshajava.support.org.objectweb.asm.tree.MethodNode;
import oshajava.support.org.objectweb.asm.tree.TryCatchBlockNode;

public class HandlerSorterAdapter extends MethodNode {
    
    private final MethodVisitor mv;
    
    public HandlerSorterAdapter(
        final MethodVisitor mv,
        final int access,
        final String name,
        final String desc,
        final String signature,
        final String[] exceptions)
    {
        super(access, name, desc, signature, exceptions);
        this.mv = mv;
    }
    
    @SuppressWarnings("unchecked")
	public void visitEnd() {
        TryCatchBlockLengthComparator comp =
                new TryCatchBlockLengthComparator(this);
        Collections.sort((List<TryCatchBlockNode>)tryCatchBlocks, comp);
        
        if (mv != null) {
            accept(mv);
        }
    }
    
}

class TryCatchBlockLengthComparator implements Comparator<TryCatchBlockNode> {
    
    private final MethodNode meth;
    
    public TryCatchBlockLengthComparator(MethodNode meth) {
        this.meth = meth;
    }
    
    public int compare(TryCatchBlockNode o1, TryCatchBlockNode o2) {
        int len1 = blockLength(o1);
        int len2 = blockLength(o2);
        return len1 - len2;
    }
    
    private int blockLength(TryCatchBlockNode block) {
        int startidx = meth.instructions.indexOf(block.start);
        int endidx = meth.instructions.indexOf(block.end);
        return endidx - startidx;
    }
    
}