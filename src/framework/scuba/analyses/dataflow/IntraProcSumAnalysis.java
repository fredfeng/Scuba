package framework.scuba.analyses.dataflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;
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

	protected Summary summary;

	protected List<BasicBlock> accessBlocksList = new ArrayList();

	protected int numberCounter;

	// analyze one method based on the cfg of this method
	public void analyze(ControlFlowGraph g) {
		// before analyzing the CFG g of some method
		// first retrieve the numbering counter of that summary that was
		// maintained last time
		numberCounter = summary.getNumberCounter();

		// create the memory locations for the parameters first
		// this should be done ONLY once! (the first time we analyze this
		// method, we can get the full list)
		if (summary.getFormals() == null) {
			// the first time to fill the paramList, we first initParamList and
			// the fill it, and later we will NOT fill this list again
			summary.initFormals();
			RegisterFactory rf = g.getRegisterFactory();
			jq_Method meth = g.getMethod();
			int numArgs = meth.getParamTypes().length;
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				Register param = rf.get(zIdx);
				summary.fillFormals(meth.getDeclaringClass(), meth, param);
			}
		}
		// we also should record the memory location of the return value
		// this is done by handleReturnStmt method and we do not need to do that
		// here, but imagine we have done it here

		BasicBlock entry = g.entry();
		accessBlocksList.clear();
		HashSet<BasicBlock> roots = new HashSet();
		HashMap<Node, Set<BasicBlock>> nodeToScc = new HashMap();
		HashMap<Set<BasicBlock>, Node> sccToNode = new HashMap();
		HashMap<BasicBlock, Node> bbToNode = new HashMap();

		roots.add(entry);
		Graph repGraph = new Graph();
		SCCHelper sccManager = new SCCHelper(g, roots);
		if (G.debug)
			System.out.println("SCC List in BBs:-----"
					+ sccManager.getComponents());
		int idx = 0;
		// compute SCC in current CFG.
		// step 1: collapse scc into one node.
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

		// step2: analyzing through normal post reverse order.
		for (Node rep : repGraph.getReversePostOrder()) {
			Set<BasicBlock> scc = nodeToScc.get(rep);
			if (scc.size() == 1) {
				BasicBlock sccB = scc.iterator().next();
				// self loop in current block.
				if (sccB.getSuccessors().contains(sccB)) {
					handleSCC(scc);
					numberCounter++;
				} else {
					this.handleBasicBlock(sccB, false);
				}
			} else {
				handleSCC(scc);
				numberCounter++;
			}
		}

		if (G.debug)
			System.out.println("Sequence of Blocks....." + accessBlocksList);
	}

	// compute the fixed-point for this scc.
	public void handleSCC(Set<BasicBlock> scc) {
		LinkedList<BasicBlock> wl = new LinkedList();
		HashMap<BasicBlock, Boolean> visit = new HashMap();
		for (BasicBlock b : scc)
			visit.put(b, false);

		// add all nodes that have preds outside the scc as entry.
		for (BasicBlock mb : scc)
			if (!scc.containsAll(mb.getPredecessors()))
				wl.add(mb);

		// strange case.
		if (wl.size() == 0)
			wl.add(scc.iterator().next());

		while (true) {
			BasicBlock bb = wl.poll();
			boolean flag = handleBasicBlock(bb, true);
			assert scc.contains(bb) : "You can't analyze the node that is out of current scc.";
			if (flag)
				for (BasicBlock b : scc)
					visit.put(b, false);
			else
				visit.put(bb, true);
			// process all succs that belongs to current scc.
			for (BasicBlock suc : bb.getSuccessors())
				if (scc.contains(suc))
					wl.add(suc);

			boolean allTrue = !visit.values().contains(false);

			if (allTrue)
				break;
		}
	}

	public boolean handleBasicBlock(BasicBlock bb, boolean isInSCC) {
		accessBlocksList.add(bb);

		if (G.debug) {
			System.out.println("=========================");
			System.out.println("Handling the basic block: ");
			System.out.println(bb);
		}

		summary.getAbstractHeap().markChanged(false);
		// handle each quad in the basicblock.
		for (Quad q : bb.getQuads()) {

			if (G.debug) {
				System.out.println("-------------------------");
				System.out.println("Handling the statement: ");
				System.out.println(q);
			}

			// handle the stmt
			summary.handleStmt(q, numberCounter, isInSCC);
			// increment the numbering counter if not in SCC
			if (!isInSCC) {
				numberCounter++;
			}

			if (G.debug) {
				System.out.println("Finish handling the statement.");
				System.out.println("-------------------------");
			}
		}

		if (G.debug) {
			System.out.println("Finish handling the basic block.");
			System.out.println("=========================");
		}
		return summary.getAbstractHeap().isChanged();
	}

	public void setSummary(Summary sum) {
		summary = sum;
	}

}