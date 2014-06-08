package framework.scuba.analyses.alias;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.CodeCache;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CICG;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.Env;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;

/**
 * Summary-based analysis. 1. Build and get a CHA-based CallGraph. 2. Compute
 * SCC 3. Run the worklist algorithm author: Yu Feng email: yufeng@cs.utexas.edu
 */

@Chord(name = "sum-java", consumes = { "rootM", "reachableM", "IM", "MM" })
public class SummaryBasedAnalysis extends JavaAnalysis {

	protected DomM domM;
	protected ProgramRel relRootM;
	protected ProgramRel relReachableM;
	protected ProgramRel relIM;
	protected ProgramRel relMM;
	protected CICG callGraph;

	HashMap<Node, Set<jq_Method>> nodeToScc = new HashMap<Node, Set<jq_Method>>();
	HashMap<Set<jq_Method>, Node> sccToNode = new HashMap<Set<jq_Method>, Node>();
	HashMap<jq_Method, Node> methToNode = new HashMap<jq_Method, Node>();

	List<jq_Method> accessSeq = new LinkedList<jq_Method>();

	IntraProcSumAnalysis intrapro = new IntraProcSumAnalysis();

	private void init() {
		getCallGraph();

		System.out.println("Total nodes in CG---------"
				+ callGraph.getNodes().size());
		// init \gamma here.
		// compute SCCs and their representative nodes.
		sumAnalyze();

		// dump interesting stats
		dumpStats();
	}

	private void dumpStats() {
		System.out.println("ALOADS------------" + Summary.aloadCnt);
		System.out.println("ASTORES------------" + Summary.astoreCnt);
		System.out.println("Array------------" + Summary.aNewArrayCnt);
		System.out.println("MultiArray------------" + Summary.aNewMulArrayCnt);
		System.out.println("Total downcast------------" + Summary.castCnt);
	}

	private void sumAnalyze() {

		if (G.debug) {
			dumpCallGraph();
			System.out.println("Root nodes: ---" + callGraph.getRoots());
			for (Set<jq_Method> scc : callGraph.getTopSortedSCCs())
				System.out.println("SCC List---" + scc);
		}

		// step 1: collapse scc into one node.
		Graph repGraph = collapseSCCs();

		LinkedList<Node> worklist = new LinkedList<Node>();

		System.out.println("total nodes: " + callGraph.getNodes());
		for (Node methNode : repGraph.getNodes())
			if ((methNode.getSuccessors().size() == 0))
				worklist.add(methNode);

		// foreach leaf in the callgraph. Add them to the worklist.
		Set<Node> visited = new HashSet<Node>();
		while (!worklist.isEmpty()) {
			Node worker = worklist.poll();
			//each node will be visited exactly once.
			if(visited.contains(worker)) 
				continue;
			// now just analyze once.
			if (allSuccsTerminated(worker.getSuccessors())) {
				workOn(worker);
				visited.add(worker);
			}
			// append worker to the end of the List.class
			else
				worklist.add(worker);

			// add m's pred to worklist
			worklist.addAll(worker.getPreds());
		}

		if (G.debug)
			System.out.println("Accessing CallGraph in this sequnce-----------"
					+ accessSeq);
	}

	/**
	 * Collapse SCCs and return the representative graph.
	 * 
	 * @return
	 */
	private Graph collapseSCCs() {
		Graph repGraph = new Graph();
		int idx = 0;
		for (Set<jq_Method> scc : callGraph.getTopSortedSCCs()) {
			// create a representation node for each scc.
			idx++;
			Node node = new Node("scc" + idx);
			nodeToScc.put(node, scc);
			sccToNode.put(scc, node);
			for (jq_Method mb : scc)
				methToNode.put(mb, node);

			repGraph.addNode(node);
		}

		for (Set<jq_Method> scc : callGraph.getTopSortedSCCs()) {
			Node cur = sccToNode.get(scc);
			for (jq_Method nb : scc) {
				// init successor.
				for (jq_Method sucb : callGraph.getSuccs(nb)) {
					if (scc.contains(sucb))
						continue;
					else {
						Node scNode = methToNode.get(sucb);
						cur.addSuccessor(scNode);
					}
				}
				// init preds.
				for (jq_Method pred : callGraph.getPreds(nb)) {
					if (scc.contains(pred))
						continue;
					else {
						Node pdNode = methToNode.get(pred);
						cur.addPred(pdNode);
					}
				}
			}
		}

		return repGraph;
	}

	// begin to work on a representative node. single node or scc.
	private void workOn(Node node) {
		// 1.get its corresponding scc
		Set<jq_Method> scc = nodeToScc.get(node);

		if (scc.size() == 1) {
			// self loop. perform scc.
			if (node.getSuccessors().contains(node))
				analyzeSCC(node);
			else
				analyze(scc.iterator().next());
		} else {
			analyzeSCC(node);
		}

		// at the end, mark it as terminated.
		node.setTerminated(true);
	}

	// check whether all successors have terminated.
	private boolean allSuccsTerminated(List<Node> succs) {
		boolean flag = true;
		for (Node node : succs)
			if (!node.isTerminated()) {
				flag = false;
				break;
			}
		return flag;
	}

	private boolean analyze(jq_Method m) {
		accessSeq.add(m);
		if (G.debug) {
			System.out.println("\n\n analyzing method " + G.count + " " + m);
			G.count++;
		}
		// do interproc
		if (G.debug) {

			System.out.println("+++++++++++++++++   " + G.count
					+ "   +++++++++++++++++");
			System.out
					.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
			System.out.println("Handling the method: ");
			System.out.println(m);
		}

		Summary summary = SummariesEnv.v().initSummary(m);
		intrapro.setSummary(summary);
		// set intrapro's number counter to be the counter of the last time the
		// summary is concluded, so that it will continue numbering from the
		// last time, to keep the numbers increasing

		if (m.getBytecode() == null)
			return false;

		ControlFlowGraph cfg = CodeCache.getCode(m);
		if (G.debug) {
			System.out.println("*****************************************");
			System.out.println(cfg.fullDump());
			System.out.println("*****************************************");
		}

		intrapro.analyze(cfg);

		// mark terminated
		if (G.dump) {
			summary.dumpSummaryToFile(new String(G.count + ""));
			summary.dumpAllMemLocsHeapToFile(new String(G.count + ""));
			summary.dumpNumberingHeap(new String(G.count + ""));
		}
		summary.validate();
		return summary.getAbsHeap().isChanged();
	}

	private void analyzeSCC(Node node) {
		Set<jq_Method> scc = nodeToScc.get(node);
		LinkedList<jq_Method> wl = new LinkedList<jq_Method>();
		// add all methods to worklist
		wl.addAll(scc);
		/*
		 * while(wl is not empty) { gamma = worker.getSummary(); m =
		 * worklist.poll(); analyze(m); reset gammaNew = m.getSummary();
		 * if(gammaNew == gamma) set else reset add pred(unterminated) }
		 */
		int counter = 0;
		while (true) {
			jq_Method worker = wl.poll();
			boolean isChanged = analyze(worker);
			if (isChanged)
				counter = 0;
			else
				counter++;

			for (jq_Method pred : callGraph.getPreds(worker))
				if (scc.contains(pred))
					wl.add(pred);

			if (counter == scc.size())
				break;
		}
	}

	/* This method will be invoked by Chord automatically. */
	public void run() {
		domM = (DomM) ClassicProject.g().getTrgt("M");
		relRootM = (ProgramRel) ClassicProject.g().getTrgt("rootM");
		relReachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
		relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relMM = (ProgramRel) ClassicProject.g().getTrgt("MM");
		// init scuba.
		init();
	}

	/**
	 * Provides the program's context-insensitive call graph.
	 * 
	 * @return The program's context-insensitive call graph.
	 */
	public ICICG getCallGraph() {
		if (callGraph == null) {
			callGraph = new CICG(domM, relRootM, relReachableM, relIM, relMM);
		}
		Env.cg = callGraph;
		return callGraph;
	}

	/* Dump method. Borrowed from Mayur. */
	public void dumpCallGraph() {
		ClassicProject project = ClassicProject.g();

		ICICG cicg = this.getCallGraph();
		domM = (DomM) project.getTrgt("M");

		PrintWriter out = OutDirUtils.newPrintWriter("cicg.dot");
		out.println("digraph G {");
		for (jq_Method m1 : cicg.getNodes()) {
			String id1 = id(m1);
			out.println("\t" + id1 + " [label=\"" + str(m1) + "\"];");
			for (jq_Method m2 : cicg.getSuccs(m1)) {
				String id2 = id(m2);
				Set<Quad> labels = cicg.getLabels(m1, m2);
				for (Quad q : labels) {
					String el = q.toJavaLocStr();
					out.println("\t" + id1 + " -> " + id2 + " [label=\"" + el
							+ "\"];");
				}
			}
		}
		out.println("}");
		out.close();

	}

	private String id(jq_Method m) {
		return "m" + domM.indexOf(m);
	}

	private static String str(jq_Method m) {
		jq_Class c = m.getDeclaringClass();
		String desc = m.getDesc().toString();
		String args = desc.substring(1, desc.indexOf(')'));
		String sign = "(" + Program.typesToStr(args) + ")";
		return c.getName() + "." + m.getName().toString() + sign;
	}

	/**
	 * Frees relations used by this program analysis if they are in memory.
	 * <p>
	 * This method must be called after clients are done exercising the
	 * interface of this analysis.
	 */
	public void free() {
		if (callGraph != null)
			callGraph.free();

	}

}
