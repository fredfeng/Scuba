package framework.scuba.analyses.dataflow;

import joeq.Compiler.Dataflow.Fact;
import joeq.Compiler.Dataflow.TransferFunction;
import jwutil.math.BitString;
import jwutil.strings.Strings;

public class GenKillTransferFunction implements TransferFunction {

    protected final BitString gen, kill;

    GenKillTransferFunction(int size) {
        this.gen = new BitString(size);
        this.kill = new BitString(size);
    }
    GenKillTransferFunction(BitString g, BitString k) {
        this.gen = g; this.kill = k;
    }

    public String toString() {
        return "   Gen: "+gen+Strings.lineSep+"   Kill: "+kill;
    }

    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.TransferFunction#apply(joeq.Compiler.Dataflow.Fact)
     */
    public Fact apply(Fact f) {
        BitVectorFact r = (BitVectorFact) f;
        BitString s = new BitString(r.fact.size());
        s.or(r.fact);
        s.minus(kill);
        s.or(gen);
        return r.makeNew(s);
    }
    
}