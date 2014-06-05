package framework.scuba.analyses.alias;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
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
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;

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
		Set<jq_Method> roots = callGraph.getRoots();

		if (G.debug) {
			dumpCallGraph();
			System.out.println("Root nodes: ---" + roots);
			for (Set<jq_Method> scc : callGraph.getTopSortedSCCs())
				System.out.println("SCC List---" + scc);
		}

		// for now, assume there is no SCC.
		LinkedList<jq_Method> worklist = new LinkedList<jq_Method>();

		System.out.println("total nodes: " + callGraph.getNodes());
		for (jq_Method meth : callGraph.getNodes())
			if ((callGraph.getSuccs(meth).size() == 0))
				worklist.add(meth);

		// foreach leaf in the callgraph. Add them to the worklist.
		HashSet<jq_Method> visited = new HashSet();
		while (!worklist.isEmpty()) {
			jq_Method worker = worklist.poll();
			// if(success terminated) {
			// now just analyze once.
			if (visited.contains(worker))
				continue;
			analyze(worker);
			visited.add(worker);
			// } else
			// append worker to the end of the List.class

			// add m's pred to worklist
			worklist.addAll(callGraph.getPreds(worker));
		}
	}

	public static int count = 0;

	private void analyze(jq_Method m) {
		System.out.println("analyzing method " + count + " " + m);
		count++;

		// do interproc
		if (G.debug) {

			System.out.println("+++++++++++++++++   " + count
					+ "   +++++++++++++++++");
			System.out
					.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
			System.out.println("Handling the method: ");
			System.out.println(m);
		}

		Summary summary = SummariesEnv.v().getSummary(m);
		intrapro.setSummary(summary);
		// set intrapro's number counter to be the counter of the last time the
		// summary is concluded, so that it will continue numbering from the
		// last time, to keep the numbers increasing

		if (m.getBytecode() == null)
			return;

		ControlFlowGraph cfg = CodeCache.getCode(m);
		if (G.debug) {
			System.out.println("*****************************************");
			System.out.println(cfg.fullDump());
			System.out.println("*****************************************");
		}
		intrapro.analyze(cfg);
		// mark terminated
		if (G.dump) {
			summary.dumpSummaryToFile(count);
			summary.dumpAllMemLocsHeapToFile(count);
		}
		summary.validate();
	}

	private void analyzeSCC() {
		// add all methods to worklist
		/*
		 * while(wl is not empty) { gamma = worker.getSummary(); m =
		 * worklist.poll(); analyze(m); reset gammaNew = m.getSummary();
		 * if(gammaNew == gamma) set else reset add pred(unterminated) }
		 */
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
