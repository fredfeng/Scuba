package framework.scuba.analyses.dataflow;

import java.util.HashSet;
import java.util.LinkedList;

import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import framework.scuba.domain.Summary;

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
    	visitSubBlocks(entry);
    }
    
    //recursive visit blocks.
    public void visitSubBlocks(BasicBlock bb) {
		handleBasicBlock(bb);
    	for(BasicBlock succ : bb.getSuccessors()) {
    		visitSubBlocks(succ);
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
