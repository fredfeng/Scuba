package framework.scuba.analyses.dataflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import framework.scuba.controller.SummaryController;
import framework.scuba.domain.AbsHeap;
import framework.scuba.domain.AbsHeapHandler;
import framework.scuba.domain.ArgDvdHandler;
import framework.scuba.domain.ScubaQuadVisitor;
import framework.scuba.domain.Summary;
import framework.scuba.domain.ToPropHandler;
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

	protected final SummaryController sumController;

	protected final AbsHeapHandler ahHandler;

	protected final ToPropHandler tpHandler;

	protected final ArgDvdHandler adHandler;

	protected final ScubaQuadVisitor qv;

	public IntraProcSumAnalysis() {
		sumController = new SummaryController();
		ahHandler = new AbsHeapHandler();
		adHandler = new ArgDvdHandler();
		tpHandler = new ToPropHandler(ahHandler);
		qv = new ScubaQuadVisitor(sumController, ahHandler);
	}

	// analyze one method based on the cfg of this method
	public void analyze(ControlFlowGraph g) {

		if (summary.getFormals() == null) {
			summary.initFormals();
			RegisterFactory rf = g.getRegisterFactory();
			jq_Method meth = g.getMethod();
			jq_Type[] paramTypes = meth.getParamTypes();
			int numArgs = meth.getParamTypes().length;
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				Register param = rf.get(zIdx);
				summary.fillFormals(meth.getDeclaringClass(), meth, param,
						paramTypes[zIdx]);
			}
		}

		BasicBlock entry = g.entry();
		HashSet<BasicBlock> roots = new HashSet<BasicBlock>();
		HashMap<Node, Set<BasicBlock>> nodeToScc = new HashMap<Node, Set<BasicBlock>>();
		HashMap<Set<BasicBlock>, Node> sccToNode = new HashMap<Set<BasicBlock>, Node>();
		HashMap<BasicBlock, Node> bbToNode = new HashMap<BasicBlock, Node>();

		roots.add(entry);
		Graph repGraph = new Graph();
		SCCHelper sccManager = new SCCHelper(g, roots);

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
					Pair<Boolean, Boolean> flag = handleSCC(scc);
					Pair<Boolean, Boolean> res = summary.isChanged();
					summary.setChanged(new Pair<Boolean, Boolean>(flag.val0
							| res.val0, flag.val1 | res.val1));
				} else {
					Pair<Boolean, Boolean> flag = handleBasicBlock(sccB, false);
					Pair<Boolean, Boolean> res = summary.isChanged();
					summary.setChanged(new Pair<Boolean, Boolean>(flag.val0
							| res.val0, flag.val1 | res.val1));
				}
			} else {
				Pair<Boolean, Boolean> flag = handleSCC(scc);
				Pair<Boolean, Boolean> res = summary.isChanged();
				summary.setChanged(new Pair<Boolean, Boolean>(flag.val0
						| res.val0, flag.val1 | res.val1));
			}
		}

		tpHandler.fillToPropSet();
		adHandler.markArgDvd();
	}

	// compute the fixed-point for this scc.
	public Pair<Boolean, Boolean> handleSCC(Set<BasicBlock> scc) {
		LinkedList<BasicBlock> wl = new LinkedList<BasicBlock>();
		Pair<Boolean, Boolean> flagScc = new Pair<Boolean, Boolean>(false,
				false);
		// add all nodes that have preds outside the scc as entry.
		for (BasicBlock mb : scc)
			if (!scc.containsAll(mb.getPredecessors()))
				wl.add(mb);

		// strange case.
		if (wl.size() == 0)
			wl.add(scc.iterator().next());

		Set<BasicBlock> set = new HashSet<BasicBlock>();

		while (true) {

			BasicBlock bb = wl.poll();
			if (set.contains(bb))
				continue;

			Pair<Boolean, Boolean> flag = handleBasicBlock(bb, true);

			flagScc.val0 = flag.val0 | flagScc.val0;
			flagScc.val1 = flag.val1 | flagScc.val1;
			assert scc.contains(bb) : "You can't analyze the node that is out of current scc.";

			// if changing the heap, we analyze all basic blocks in the scc
			// again (conservative)
			if (flag.val0)
				set.clear();
			else
				set.add(bb);

			// xinyu's algorithm: use counter to achieve O(1)
			if (set.size() == scc.size())
				break;

			// process all succs that belongs to current scc.
			for (BasicBlock suc : bb.getSuccessors())
				if (scc.contains(suc))
					wl.add(suc);
		}
		return flagScc;
	}

	public Pair<Boolean, Boolean> handleBasicBlock(BasicBlock bb,
			boolean isInSCC) {
		Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false, false);
		// handle each quad in the basicblock.
		for (Quad q : bb.getQuads()) {
			// handle the stmt
			Pair<Boolean, Boolean> flagStmt = handleStmt(q,
					summary.getAbsHeap());

			flag.val0 = flagStmt.val0 | flag.val0;
			flag.val1 = flagStmt.val1 | flag.val1;

		}

		return flag;
	}

	public Pair<Boolean, Boolean> handleStmt(Quad quad, AbsHeap absHeap) {
		absHeap.markChanged(new Pair<Boolean, Boolean>(false, false));
		if (quad.getOperator() instanceof Invoke) {
			return absHeap.isChanged();
		}
		quad.accept(qv);
		return absHeap.isChanged();
	}

	public void setEverything(Summary sum) {
		summary = sum;
		qv.setSummary(sum);
		tpHandler.setSummary(sum);
		ahHandler.setAbsHeap(sum.getAbsHeap());
		adHandler.setAbsHeap(sum.getAbsHeap());
	}

}