package framework.scuba.analyses.dataflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.analyses.alias.SummaryBasedAnalysis;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;
import framework.scuba.helper.SCCHelper;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;
import framework.scuba.utils.StringUtil;

/**
 * Intra-proc summary-based analysis Check the rules in Figure 8 of our paper.
 * 
 * @author yufeng
 * 
 */
public class IntraProcSumAnalysis {

	protected Summary summary;

	protected List<BasicBlock> accessBlocksList = new ArrayList<BasicBlock>();

	protected int numToAssign;

	// analyze one method based on the cfg of this method
	public void analyze(ControlFlowGraph g) {
		// before analyzing the CFG g of some method
		// first retrieve the numbering counter of that summary that was
		// maintained last time
		// getNumberCounter gets the number counter that is used last time
		this.numToAssign = summary.getCurrNumCounter() + 1;
		// for dbg

		// create the memory locations for the parameters first if has not
		// this should be done ONLY once! (the first time we analyze this
		// method, we can get the full list)
		if (summary.getFormals() == null) {
			summary.initFormals();
			RegisterFactory rf = g.getRegisterFactory();
			jq_Method meth = g.getMethod();
			int numArgs = meth.getParamTypes().length;
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				Register param = rf.get(zIdx);
				summary.fillFormals(meth.getDeclaringClass(), meth, param);
			}
		}

		BasicBlock entry = g.entry();
		accessBlocksList.clear();
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
		int tmp = 0;
		for (Node rep : repGraph.getReversePostOrder()) {
			tmp++;
			if (G.dbgSCC) {
				StringUtil.reportInfo("Analyzing BB SCC times: " + tmp);
			}
			Set<BasicBlock> scc = nodeToScc.get(rep);
			if (scc.size() == 1) {
				BasicBlock sccB = scc.iterator().next();
				// self loop in current block.
				if (sccB.getSuccessors().contains(sccB)) {
					if (G.dbgSCC) {
						StringUtil.reportInfo("I am here: 1");
					}
					boolean flag = handleSCC(scc);
					summary.setChanged(flag || summary.isChanged());

					numToAssign = summary.getCurrNumCounter() + 1;
				} else {
					if (G.dbgSCC) {
						StringUtil.reportInfo("I am here: 2");
					}
					boolean flag = handleBasicBlock(sccB, false);
					summary.setChanged(flag || summary.isChanged());
				}
			} else {
				if (G.dbgSCC) {
					StringUtil.reportInfo("I am here: 3");
				}
				boolean flag = handleSCC(scc);
				summary.setChanged(flag || summary.isChanged());
				numToAssign = summary.getCurrNumCounter() + 1;
			}
		}

		if (G.info) {
			System.out.println("[Info] Sequence of visiting basic blocks:\n"
					+ accessBlocksList);
		}
	}

	public static int sccProgress = 0;

	// compute the fixed-point for this scc.
	public boolean handleSCC(Set<BasicBlock> scc) {
		LinkedList<BasicBlock> wl = new LinkedList<BasicBlock>();
		boolean flagScc = false;
		// add all nodes that have preds outside the scc as entry.
		for (BasicBlock mb : scc)
			if (!scc.containsAll(mb.getPredecessors()))
				wl.add(mb);

		// strange case.
		if (wl.size() == 0)
			wl.add(scc.iterator().next());

		Set<BasicBlock> set = new HashSet<BasicBlock>();
		sccProgress = 0;
		while (true) {
			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- SCC progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]" + set.size()
						+ " out of " + scc.size());
				StringUtil.reportInfo("Sunny -- SCC progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ " iteration: " + ++sccProgress + "-th");
			}
			BasicBlock bb = wl.poll();
			if (set.contains(bb))
				continue;

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- SCC progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "handling BB: " + bb);
			}

			boolean flag = handleBasicBlock(bb, true);

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- SCC progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "finish BB: " + bb + " result: " + flag);
			}

			flagScc = flagScc || flag;
			assert scc.contains(bb) : "You can't analyze the node that is out of current scc.";
			if (flag)
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

	public static int bbProgress = 0;
	public static int tmp1 = 0;
	public static boolean opt = false;

	public boolean handleBasicBlock(BasicBlock bb, boolean isInSCC) {
		accessBlocksList.add(bb);
		boolean flag = false;
		// handle each quad in the basicblock.
		bbProgress = 0;
		for (Quad q : bb.getQuads()) {
			if (G.dbgRet) {
				StringUtil.reportInfo("Full dump: " + bb.fullDump());
			}
			// handle the stmt
			bbProgress++;

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- BB progress: " + bbProgress
						+ "-th iteration" + " out of " + bb.size());
			}

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- BB progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "handling stmt: " + q);
			}

			boolean flagStmt = summary.handleStmt(q, numToAssign, isInSCC);

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- BB progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "finish stmt: " + q + " result: " + flagStmt);
			}

			opt = flagStmt;
			if (G.dbgSCC) {
				if (flagStmt) {
					StringUtil.reportInfo("Rain: [" + G.countScc
							+ "] this is the stmt: " + bbProgress + "-th");
					bb.fullDump();
				}
			}
			flag = flag || flagStmt;

			// increment the numbering counter properly:
			// we only increment the counter here for basic blocks that are not
			// in some SCC, for basic blocks in an SCC we increment outside
			if (!isInSCC) {
				numToAssign = summary.getCurrNumCounter() + 1;
			}
		}
		return flag;
	}

	public void setSummary(Summary sum) {
		summary = sum;
	}

}