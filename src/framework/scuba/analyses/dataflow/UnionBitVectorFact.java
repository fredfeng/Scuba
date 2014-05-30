package framework.scuba.analyses.dataflow;

import joeq.Compiler.Dataflow.Fact;
import jwutil.math.BitString;

public class UnionBitVectorFact extends BitVectorFact {

    protected UnionBitVectorFact(int size) {
        super(size);
    }
    
    protected UnionBitVectorFact(BitString s) {
        super(s);
    }
    
    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Fact#merge(joeq.Compiler.Dataflow.Fact)
     */
    public Fact merge(Fact that) {
        BitVectorFact r = (BitVectorFact) that;
        BitString s = new BitString(this.fact.size());
        s.or(this.fact);
        boolean b = s.or(r.fact);
        if (!b) return this;
        else return new UnionBitVectorFact(s);
    }
    
    public BitVectorFact makeNew(BitString s) {
        return new UnionBitVectorFact(s);
    }
}