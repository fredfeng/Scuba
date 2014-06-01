package framework.scuba.analyses.dataflow;

import java.util.HashSet;

import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.domain.Summary;
import framework.scuba.domain.AbstractHeap.VariableType;
import framework.scuba.helper.SCCHelper;

/**
 * Intra-proc summary-based analysis
 * Check the rules in Figure 8 of our paper.
 * @author yufeng
 *
 */
public class IntraProcSumAnalysis {  
    
    Summary summary; 

    //now we assume there is no scc in basicblock.
    public void analyze(ControlFlowGraph g) {
    	BasicBlock entry = g.entry();
    	HashSet<BasicBlock> roots = new HashSet();
    	roots.add(entry);
    	SCCHelper sccManager = new SCCHelper(g, roots);
    	System.out.println("SCC List in BBs:-----" + sccManager.getComponents());
    	//compute SCC in current CFG.
    	//for now, use the default reversePostOrder from joeq. 
    	//I will implement a new version to support scc later.
    	g.reversePostOrder();
    	for(BasicBlock bb : g.reversePostOrder()) {
    		handleBasicBlock(bb);
    	}
    }
    
    public boolean handleBasicBlock(BasicBlock bb) {
    	summary.getAbstractHeap().isChanged = false;
    	//handle each quad in the basicblock.
    	for(Quad q : bb.getQuads())
    		summary.handleStmt(q);
    	
    	return summary.getAbstractHeap().isChanged;
    }
    
    public void setSummary(Summary sum) {
    	summary = sum;
    }
}
