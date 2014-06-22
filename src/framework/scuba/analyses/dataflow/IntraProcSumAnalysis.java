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
import chord.util.tuple.object.Pair;
import framework.scuba.analyses.alias.SummaryBasedAnalysis;
import framework.scuba.domain.SummariesEnv;
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

	// analyze one method based on the cfg of this method
	public void analyze(ControlFlowGraph g) {

		if (G.dbgPermission) {
			StringUtil.reportInfo("dbgPermission");
			StringUtil.reportInfo("dbgPermission: " + " analyzing method: "
					+ g.getMethod());
			StringUtil.reportInfo("dbgPermission: " + g.fullDump());
		}

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

		if (G.info) {
			System.out.println("[Info] Sequence of visiting basic blocks:\n"
					+ accessBlocksList);
		}
	}

	public static int sccProgress = 0;

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
		sccProgress = 0;
		int smash = 0;
		while (true) {
			if (G.dbgSmashing) {
				smash++;
				if (summary != null && smash >= 30) {
					summary.dumpSummaryToFile("$equals");
					assert false;
				}
			}
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

			Pair<Boolean, Boolean> flag = handleBasicBlock(bb, true);

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- SCC progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "finish BB: " + bb + " result: " + flag);
			}

			flagScc.val0 = flag.val0 | flagScc.val0;
			flagScc.val1 = flag.val1 | flagScc.val1;
			assert scc.contains(bb) : "You can't analyze the node that is out of current scc.";

			// TODO
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

	public static int bbProgress = 0;
	public static int tmp1 = 0;
	public static boolean opt = false;
	public static int sm = 0;

	public Pair<Boolean, Boolean> handleBasicBlock(BasicBlock bb,
			boolean isInSCC) {
		accessBlocksList.add(bb);
		Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false, false);
		// handle each quad in the basicblock.
		bbProgress = 0;
		for (Quad q : bb.getQuads()) {
			if (G.dbgRet) {
				System.out.println("Full dump");
				System.out.println(bb.fullDump());
			}
			// handle the stmt
			bbProgress++;

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- BB progress: " + bbProgress
						+ "-th iteration" + " out of " + bb.size());
			}

			if (G.dbgMatch) {
				System.out.println("in the scc: " + SummaryBasedAnalysis.inS);
				StringUtil.reportInfo("Sunny -- BB progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "handling stmt: " + q);
			}

			sm++;
			if (G.dbgSmashing) {
				if (sm == 93) {
					summary.dumpSummaryToFile("$before$93");
				}
				if (sm == 93) {
					summary.dumpSummaryToFile("$before$94");
				}

			}
			Pair<Boolean, Boolean> flagStmt = summary.handleStmt(q);

			if (G.dbgSmashing) {
				System.out.println("dbgSmashing: " + "flag result: " + flagStmt
						+ " id: " + sm);
			}
			if (G.dbgSmashing) {
				if (sm == 93) {
					summary.dumpSummaryToFile("$after$93");
				}
				if (sm == 93) {
					summary.dumpSummaryToFile("$after$94");
				}

			}

			if (G.dbgRef) {
				System.out.println("dbgRef: " + "stmt result: " + flagStmt);
			}

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- BB progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " ]"
						+ "finish stmt: " + q + " result: " + flagStmt);
			}

			flag.val0 = flagStmt.val0 | flag.val0;
			flag.val1 = flagStmt.val1 | flag.val1;

		}
		if (G.dbgRef) {
			System.out.println("dbgRef: " + "basic block result: " + flag);
		}
		return flag;
	}

	public void setSummary(Summary sum) {
		summary = sum;
	}

}