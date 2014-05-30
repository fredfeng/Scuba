package framework.scuba.analyses.dataflow;

import joeq.Compiler.Dataflow.Fact;
import jwutil.math.BitString;

public abstract class BitVectorFact implements Fact {

    protected final BitString fact;

    protected BitVectorFact(int size) {
        this.fact = new BitString(size);
    }
    
    protected BitVectorFact(BitString s) {
        this.fact = s;
    }

    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Fact#merge(Compiler.Dataflow.Fact)
     */
    public abstract Fact merge(Fact that);
    
    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Fact#equals(Compiler.Dataflow.Fact)
     */
    public boolean equals(Fact that) {
        return this.fact.equals(((BitVectorFact) that).fact);
    }
    
    public String toString() {
        return fact.toString();
    }
    
    public abstract BitVectorFact makeNew(BitString s);
    
}