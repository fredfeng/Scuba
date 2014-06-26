package framework.scuba.analyses.invk_virtual;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,i) such that method m contains
 * method invocation quad i.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MIVirtual",
    sign = "M0,I0:I0xM0"
)
public class RelMIVirtual extends ProgramRel {
    public void fill() {
        DomM domM = (DomM) doms[0];
        DomI domI = (DomI) doms[1];
        int numI = domI.size();
        for (int iIdx = 0; iIdx < numI; iIdx++) {
            Quad q = (Quad) domI.get(iIdx);
            jq_Method m = q.getMethod();
            int mIdx = domM.indexOf(m);
            assert (mIdx >= 0);    
            Operator op = q.getOperator();
            if (op instanceof InvokeVirtual || op instanceof InvokeInterface) {
            	add(mIdx, iIdx);
            }
            
            
        }
    }
}
