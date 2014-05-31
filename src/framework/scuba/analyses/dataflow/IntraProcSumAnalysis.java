package framework.scuba.analyses.dataflow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import joeq.Compiler.Dataflow.Fact;
import joeq.Compiler.Dataflow.Problem;
import joeq.Compiler.Dataflow.Solver;
import joeq.Compiler.Dataflow.TransferFunction;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import jwutil.graphs.EdgeGraph;
import jwutil.graphs.Graph;
import jwutil.graphs.ReverseGraph;
import jwutil.math.BitString;
import jwutil.util.Assert;
import framework.scuba.domain.Summary;

/**
 * Intra-proc summary-based analysis
 * Check the rules in Figure 8 of our paper.
 * @author yufeng
 *
 */
public class IntraProcSumAnalysis extends Problem{
    Map<BasicBlock, TransferFunction> transferFunctions;
    
    Fact emptySet;
    
    TransferFunction emptyTF;
    
    Summary summary; 

    public Solver mySolver;
    
    static final boolean TRACE = false;

    public void initialize(Graph g) {
        Graph g2 = ((EdgeGraph) g).getGraph();
        ControlFlowGraph cfg = (ControlFlowGraph) ((ReverseGraph) g2).getGraph();
        
        if (TRACE) System.out.println("Initializing IntraProcSumAnalysis.");
        if (TRACE) System.out.println(cfg.fullDump());
        
        // size of bit vector is bounded by the number of registers.
        int bitVectorSize = cfg.getRegisterFactory().size() + 1;
        
        if (TRACE) System.out.println("Bit vector size: "+bitVectorSize);
        
        transferFunctions = new HashMap();
        emptySet = new UnionBitVectorFact(bitVectorSize);
        emptyTF = new GenKillTransferFunction(bitVectorSize);
        
        for (BasicBlock bb : cfg.reversePostOrder()) {
            BitString gen = new BitString(bitVectorSize);
            BitString kill = new BitString(bitVectorSize);
            for (int j = bb.size() - 1; j >= 0; j--) {
                Quad q = bb.getQuad(j);
                for (RegisterOperand k : q.getDefinedRegisters()) {
                        Register r = k.getRegister();
                    int index = r.getNumber() + 1;
                    kill.set(index);
                    gen.clear(index);
                }
                for (RegisterOperand k : q.getUsedRegisters()) {
                    Register r = k.getRegister();
                    int index = r.getNumber() + 1;
                    gen.set(index);
                }
            }
            GenKillTransferFunction tf = new GenKillTransferFunction(gen, new BitString(bitVectorSize));
            tf.gen.or(gen);
            tf.kill.or(kill);
            Assert._assert(!transferFunctions.containsKey(bb));
            transferFunctions.put(bb, tf);
        }
        if (TRACE) {
            System.out.println("Transfer functions:");
            for (Iterator i = transferFunctions.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry e = (Map.Entry) i.next();
                System.out.println(e.getKey());
                System.out.println(e.getValue());
            }
        }
    }

    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Problem#direction()
     */
    public boolean direction() {
        return false;
    }

    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Problem#boundary()
     */
    public Fact boundary() {
        return emptySet;
    }

    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Problem#interior()
     */
    public Fact interior() {
        return emptySet;
    }

    /* (non-Javadoc)
     * @see joeq.Compiler.Dataflow.Problem#getTransferFunction(java.lang.Object)
     */
    public TransferFunction getTransferFunction(Object e) {
        TransferFunction tf = (TransferFunction) transferFunctions.get(e);
        if (tf == null) tf = emptyTF;
        return tf;
    }
   
    public boolean isLiveAtOut(BasicBlock bb, Register r) {
        if (bb.getNumberOfSuccessors() > 0)
            bb = bb.getSuccessors().get(0);
        BitVectorFact f = (BitVectorFact) mySolver.getDataflowValue(bb);
        if (f == null) throw new RuntimeException(bb.toString()+" reg "+r);
        return f.fact.get(r.getNumber()+1);
    }
    
    public boolean isLiveAtIn(BasicBlock bb, Register r) {
        BitVectorFact f = (BitVectorFact) mySolver.getDataflowValue(bb);
        return f.fact.get(r.getNumber()+1);
    }
    
    public void setLiveAtIn(BasicBlock bb, Register r) {
        BitVectorFact f = (BitVectorFact) mySolver.getDataflowValue(bb);
        f.fact.set(r.getNumber()+1);
        GenKillTransferFunction tf = (GenKillTransferFunction) transferFunctions.get(bb);
        tf.gen.set(r.getNumber()+1);
    }
    
    public void setKilledAtIn(BasicBlock bb, Register r) {
        BitVectorFact f = (BitVectorFact) mySolver.getDataflowValue(bb);
        f.fact.clear(r.getNumber()+1);
        GenKillTransferFunction tf = (GenKillTransferFunction) transferFunctions.get(bb);
        tf.kill.set(r.getNumber()+1);
    }
    
    public void setSummary(Summary sum) {
    	summary = sum;
    }
}
