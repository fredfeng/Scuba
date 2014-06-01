package framework.scuba.analyses.dataflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import framework.scuba.domain.Summary;
import framework.scuba.helper.SCCHelper;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;

/**
 * Intra-proc summary-based analysis Check the rules in Figure 8 of our paper.
 * 
 * @author yufeng
 * 
 */
public class IntraProcSumAnalysis {

	Summary summary;

	public void analyze(ControlFlowGraph g) {
		BasicBlock entry = g.entry();
		HashSet<BasicBlock> roots = new HashSet();
		HashMap<Node, Set<BasicBlock>> nodeToScc = new HashMap();
		HashMap<Set<BasicBlock>, Node> sccToNode = new HashMap();
		HashMap<BasicBlock, Node> bbToNode = new HashMap();

		roots.add(entry);
		Graph repGraph = new Graph();
		SCCHelper sccManager = new SCCHelper(g, roots);
		System.out
				.println("SCC List in BBs:-----" + sccManager.getComponents());
		int idx = 0;
		// compute SCC in current CFG.
		//step 1: collapse scc into one node.
		for (Set<BasicBlock> scc : sccManager.getComponents()) {
			// create a representation node for each scc.
			idx++;
			Node node = new Node("scc" + idx);
			nodeToScc.put(node, scc);
			sccToNode.put(scc, node);
			for (BasicBlock mb : scc)
				bbToNode.put(mb, node);

			repGraph.addNode(node);
			if (scc.contains(entry))
				repGraph.setEntry(node);
		}

		for (Set<BasicBlock> scc : sccManager.getComponents()) {
			Node cur = sccToNode.get(scc);
			for (BasicBlock nb : scc) {
				for (BasicBlock sucb : nb.getSuccessors()) {
					if (scc.contains(sucb))
						continue;
					else {
						Node scNode = bbToNode.get(sucb);
						cur.addSuccessor(scNode);
					}
				}
			}
		}

		//step2: analyzing through normal post reverse order.
		for (Node rep : repGraph.getReversePostOrder()) {
			Set<BasicBlock> scc = nodeToScc.get(rep);
			if (scc.size() == 1) {
				BasicBlock sccB = scc.iterator().next();
				// self loop in current block.
				if (sccB.getSuccessors().contains(sccB))
					handleSCC(scc);
				else
					this.handleBasicBlock(sccB);
			} else {
				handleSCC(scc);
			}
		}
	}

	// compute the fixed-point for this scc.
	public void handleSCC(Set<BasicBlock> scc) {
		LinkedList<BasicBlock> wl = new LinkedList();
		HashMap<BasicBlock, Boolean> visit = new HashMap();
		for (BasicBlock b : scc)
			visit.put(b, false);

		//add all nodes that have preds outside the scc as entry.
		for(BasicBlock mb : scc)
			if(!scc.containsAll(mb.getPredecessors()))
				wl.add(mb);
		
		//strange case.
		if(wl.size() == 0)
			wl.add(scc.iterator().next());
		
		while (true) {
			BasicBlock bb = wl.poll();
			boolean flag = handleBasicBlock(bb);
			if (flag)
				for (BasicBlock b : scc)
					visit.put(b, false);
			else
				visit.put(bb, true);
			//process all succs.
			wl.addAll(bb.getSuccessors());
			boolean allTrue = !visit.values().contains(false);

			if (allTrue)
				break;
		}
	}

	public boolean handleBasicBlock(BasicBlock bb) {

		System.out.println("=========================");
		System.out.println("Handling the basic block: ");
		System.out.println(bb);

		summary.getAbstractHeap().isChanged = false;
		// handle each quad in the basicblock.
		for (Quad q : bb.getQuads()) {
			
			System.out.println("-------------------------");
			System.out.println("Handling the statement: ");
			System.out.println(q);

			summary.handleStmt(q);

			System.out.println("Finish handling the statement.");
			System.out.println("-------------------------");
		}

		System.out.println("Finish handling the basic block.");
		System.out.println("=========================");
		return summary.getAbstractHeap().isChanged;
	}

	public void setSummary(Summary sum) {
		summary = sum;
	}
}
