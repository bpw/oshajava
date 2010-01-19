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
import oshajava.support.org.objectweb.asm.Label;
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
    
    public void visitEnd() {
        TryCatchBlockLengthComparator comp =
                new TryCatchBlockLengthComparator(this);
        Collections.sort(tryCatchBlocks, comp);
        
        if (mv != null) {
            accept(mv);
        }
    }
    
}

class TryCatchBlockLengthComparator implements Comparator {
    
    private final MethodNode meth;
    
    public TryCatchBlockLengthComparator(MethodNode meth) {
        this.meth = meth;
    }
    
    public int compare(Object o1, Object o2) {
        int len1 = blockLength((TryCatchBlockNode)o1);
        int len2 = blockLength((TryCatchBlockNode)o2);
        return len1 - len2;
    }
    
    private int blockLength(TryCatchBlockNode block) {
        int startidx = meth.instructions.indexOf(block.start);
        int endidx = meth.instructions.indexOf(block.end);
        return endidx - startidx;
    }
    
}