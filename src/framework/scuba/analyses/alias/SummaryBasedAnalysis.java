package framework.scuba.analyses.alias;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.CodeCache;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICG;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_lbool;

import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.AbstractMemLoc;
import framework.scuba.domain.Env;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;
import framework.scuba.helper.SCCHelper4CG;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;
import framework.scuba.utils.StringUtil;

/**
 * Summary-based analysis. 1. Build and get a CHA-based CallGraph. 2. Compute
 * SCC 3. Run the worklist algorithm author: Yu Feng email: yufeng@cs.utexas.edu
 */

@Chord(name = "sum-java", consumes = { "rootM", "reachableM", "IM", "MM", "cha" })
public class SummaryBasedAnalysis extends JavaAnalysis {

	protected DomM domM;
	protected ProgramRel relRootM;
	protected ProgramRel relReachableM;
	protected ProgramRel relIM;
	protected ProgramRel relMM;
	protected ProgramRel relCHA;

	protected CICG callGraph;

	HashMap<Node, Set<jq_Method>> nodeToScc = new HashMap<Node, Set<jq_Method>>();
	HashMap<Set<jq_Method>, Node> sccToNode = new HashMap<Set<jq_Method>, Node>();
	HashMap<jq_Method, Node> methToNode = new HashMap<jq_Method, Node>();

	List<jq_Method> accessSeq = new LinkedList<jq_Method>();

	IntraProcSumAnalysis intrapro = new IntraProcSumAnalysis();

	private void init() {
		getCallGraph();

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

	// private void dumpMeth() {
	// //elements:()Ljava/util/Enumeration;@java.security.Permissions
	// //<clinit>:()V@sun.nio.cs.StandardCharsets
	// toEnvironmentBlock:([I)[B@java.lang.ProcessEnvironment$StringEnvironment
	// jq_Method meth = Program.g().getMethod(
	// "elements:()Ljava/util/Enumeration;@java.security.Permissions");
	// ControlFlowGraph cfg = meth.getCFG();
	// for(BasicBlock bb : cfg.reversePostOrder()) {
	// System.out.println(bb.fullDump());
	// for(Quad q : bb.getQuads()) {
	// if(O)
	// }
	// }
	// }

	private void sumAnalyze() {

		if (G.dump) {
			dumpCallGraph();
		}

		// dumpMeth();

		// step 1: collapse scc into one node.
		Graph repGraph = collapseSCCs();

		if (G.tuning) {
			StringUtil.reportInfo("Total # of SCCs: "
					+ repGraph.getNodes().size());
			StringUtil.reportInfo("Total reachableM: " + relReachableM.size());
		}

		LinkedList<Node> worklist = new LinkedList<Node>();

		for (Node methNode : repGraph.getNodes())
			if ((methNode.getSuccessors().size() == 0)) {
				assert methNode != null : "Entry can not be null";
				worklist.add(methNode);
			}

		// foreach leaf in the callgraph. Add them to the worklist.
		Set<Node> visited = new HashSet<Node>();
		while (!worklist.isEmpty()) {
			Node worker = worklist.poll();
			// each node will be visited exactly once.
			if (visited.contains(worker))
				continue;
			// now just analyze once.
			assert worker != null : "Worker can not be null";
			if (allTerminated(worker.getSuccessors())) {
				if (G.tuning)
					StringUtil.reportInfo("Before work on " + worker);
				workOn(worker);

				visited.add(worker);
			}
			// append worker to the end of the List.class
			else
				worklist.add(worker);

			// add m's pred to worklist
			worklist.addAll(worker.getPreds());
		}

		if (G.info)
			System.out.println("[Info] Accessing CallGraph in this sequnce:\n"
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

		Set<jq_Method> sccs = new HashSet<jq_Method>();
		Set<jq_Method> cgs = new HashSet<jq_Method>();

		cgs.addAll(callGraph.getNodes());

		SCCHelper4CG s4g = new SCCHelper4CG(callGraph, callGraph.getRoots());

		int maxSize = 0;
		for (Set<jq_Method> scc : s4g.getComponents()) {
			// create a representation node for each scc.
			idx++;
			if (scc.size() > maxSize)
				maxSize = scc.size();
			Node node = new Node("scc" + idx);
			nodeToScc.put(node, scc);
			sccToNode.put(scc, node);
			for (jq_Method mb : scc)
				methToNode.put(mb, node);

			repGraph.addNode(node);
			sccs.addAll(scc);
		}

		// FIXME: This is a bug in chord. The total number of SCCs is not equal
		// to the total number of reachable methods. Adding the missing methods
		// to scc list.
		cgs.removeAll(sccs);
		for (jq_Method miss : cgs) {
			idx++;
			Node node = new Node("scc" + idx);
			Set<jq_Method> newScc = new HashSet<jq_Method>();
			newScc.add(miss);
			nodeToScc.put(node, newScc);
			sccToNode.put(newScc, node);
			methToNode.put(miss, node);
		}

		for (Set<jq_Method> scc : s4g.getComponents()) {
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
		if (G.tuning) {
			G.countScc++;
			StringUtil.reportInfo("Working on SCC: " + G.countScc
					+ nodeToScc.get(node));
			StringUtil.reportInfo("Size of SCC: " + nodeToScc.get(node).size());
		}
		long startSCC = System.nanoTime();

		assert !node.isTerminated() : "Should not analyze a node twice.";

		// 1.get its corresponding scc
		Set<jq_Method> scc = nodeToScc.get(node);

		if (scc.size() == 1) {
			// self loop. perform scc.
			if (node.getSuccessors().contains(node)) {
				analyzeSCC(node);
			} else {
				analyze(scc.iterator().next());
			}
		} else {
			analyzeSCC(node);
		}

		// at the end, mark it as terminated.
		terminateAndDoGC(node);

		if (G.dbgMatch) {
			for (jq_Method m : scc) {
				Summary sum = SummariesEnv.v().getSummary(m);
				if (sum == null) {
					continue;
				}
				StringUtil.reportInfo("[" + G.countScc
						+ "] Blowup: for method: " + m);
				StringUtil.reportInfo("Blowup: " + "successors: "
						+ Env.cg.getSuccs(m));
				sum.printCalleeHeapInfo("Blowup");
			}
		}

		if (G.tuning) {
			long endSCC = System.nanoTime();
			StringUtil.reportSec("Analyzing SCC in ", startSCC, endSCC);
		}
		if (G.stat) {
			if (G.countScc >= 1919) {
				try {
					dumpStatistics();
				} catch (Z3Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	// mark current node as terminated and perform GC on its successor, if
	// possible.
	private void terminateAndDoGC(Node node) {
		node.setTerminated(true);
		if (!SummariesEnv.v().forceGc())
			return;
		for (Node succ : node.getSuccessors()) {
			// for each successor, if all its preds are terminated, we can gc
			// this successor.
			if (allTerminated(succ.getPreds())) {
				StringUtil.reportInfo("GC node: " + succ);
				for (jq_Method meth : nodeToScc.get(succ)) {
					Summary sumGC = SummariesEnv.v().removeSummary(meth);
					if (sumGC != null) {
						assert sumGC != null : "Summary to be GC can not be null";
						sumGC.gcAbsHeap();
						sumGC = null;
						System.gc();
						StringUtil.reportInfo("GC abstract heap for: " + meth);
					}
				}
			}

		}
	}

	// check whether all nodes have terminated.
	private boolean allTerminated(List<Node> succs) {
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

		if (m.getBytecode() == null) {
			if (G.info) {
				System.err.println("ERROR: the method: " + m
						+ " is lacking models");
			}
			return false;
		}

		Summary summary = SummariesEnv.v().initSummary(m);
		summary.setChanged(false);
		intrapro.setSummary(summary);
		// set intrapro's number counter to be the counter of the last time the
		// summary is concluded, so that it will continue numbering from the
		// last time, to keep the numbers increasing

		ControlFlowGraph cfg = CodeCache.getCode(m);
		if (G.dump) {
			System.out.println("*****************************************");
			System.out.println(cfg.fullDump());
			System.out.println("*****************************************");
		}

		intrapro.analyze(cfg);

		if (G.tuning) {
			StringUtil.reportTotalTime("Total Time to generate Constraint: ",
					G.genCstTime);
			StringUtil.reportTotalTime("Total Time to Constraint Operation: ",
					G.cstOpTime);
			StringUtil.reportInfo("Max Constraint: " + G.maxCst);

			StringUtil.reportInfo("Free memory (MB): "
					+ Runtime.getRuntime().freeMemory() / (1024 * 1024));
		}
		if (G.validate) {
			summary.validate();
		}

		summary.setHasAnalyzed();
		return summary.isChanged();
	}

	public static int cgProgress = 0;
	public static boolean inS = false;

	private void analyzeSCC(Node node) {
		inS = true;
		Set<jq_Method> scc = nodeToScc.get(node);

		if (G.dbgPermission) {
			if (G.countScc == G.sample) {
				StringUtil.reportInfo("Evils: begin SCC");
				Set<jq_Method> evils = new HashSet<jq_Method>();
				for (jq_Method m : scc) {
					Summary sum = SummariesEnv.v().getSummary(m);
					if (sum == null) {
						continue;
					}
					if (sum.getHeapSize() > 300) {
						evils.add(m);
					}
				}
				for (jq_Method m : evils) {
					StringUtil.reportInfo("Evils: " + m);
				}
			}
		}

		LinkedList<jq_Method> wl = new LinkedList<jq_Method>();
		// add all methods to worklist
		wl.addAll(scc);
		/*
		 * while(wl is not empty) { gamma = worker.getSummary(); m =
		 * worklist.poll(); analyze(m); reset gammaNew = m.getSummary();
		 * if(gammaNew == gamma) set else reset add pred(unterminated) }
		 */
		Set<jq_Method> set = new HashSet<jq_Method>();
		cgProgress = 0;
		int times = 0;
		while (true) {
			times++;
			if (times == 50) {
				if (G.dbgPermission) {
					if (G.countScc == G.sample) {
						StringUtil.reportInfo("Evils: begin Iteration");
						Set<jq_Method> evils = new HashSet<jq_Method>();
						for (jq_Method m : scc) {
							Summary sum = SummariesEnv.v().getSummary(m);
							if (sum == null) {
								continue;
							}
							if (sum.getHeapSize() > 300) {
								evils.add(m);
							}
						}
						for (jq_Method m : evils) {
							StringUtil.reportInfo("Evils: " + m);
						}
					}
				}
			}
			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- CG progress: " + set.size()
						+ " out of " + scc.size());
				StringUtil.reportInfo("Sunny -- CG progress: " + " iteration: "
						+ ++cgProgress + "-th");
			}
			jq_Method worker = wl.poll();
			if (set.contains(worker))
				continue;
			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- CG progress: "
						+ "handling method: " + worker);
			}

			boolean changed = analyze(worker);

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- CG progress: "
						+ "finish method: " + worker + " result: " + changed);
			}

			if (changed)
				set.clear();
			else
				set.add(worker);

			if (G.tuning)
				StringUtil.reportInfo("SCC counter: " + set.size() + ":"
						+ worker);

			if (set.size() == scc.size())
				break;

			for (jq_Method pred : callGraph.getPreds(worker))
				if (scc.contains(pred))
					wl.add(pred);
		}

		inS = false;
	}

	/* This method will be invoked by Chord automatically. */
	public void run() {
		domM = (DomM) ClassicProject.g().getTrgt("M");
		relRootM = (ProgramRel) ClassicProject.g().getTrgt("rootM");
		relReachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
		relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relMM = (ProgramRel) ClassicProject.g().getTrgt("MM");
		relCHA = (ProgramRel) ClassicProject.g().getTrgt("cha");
		// pass relCha ref to SummariesEnv
		SummariesEnv.v().setCHA(relCHA);
		Env.buildClassHierarchy();

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

	public P2Set query(jq_Class clazz, jq_Method method, Register variable) {
		jq_Method entry = Program.g().getMainMethod();
		Summary sum = SummariesEnv.v().getSummary(entry);
		assert (sum != null) : "the entry method should have a summary!";
		P2Set ret = sum.getP2Set(clazz, method, variable);
		return ret;
	}

	public void dumpStatistics() throws Z3Exception {
		StringBuilder b = new StringBuilder("");
		Map<jq_Method, Summary> sums = SummariesEnv.v().getSums();
		int total_all = 0;
		int t_all = 0;
		int f_all = 0;
		int other_all = 0;
		for (jq_Method m : sums.keySet()) {
			int total = 0;
			int t = 0;
			int f = 0;
			int other = 0;
			Map<Pair<AbstractMemLoc, FieldElem>, P2Set> absHeap = sums.get(m)
					.getAbsHeap().getHeap();
			for (Pair<AbstractMemLoc, FieldElem> pair : absHeap.keySet()) {
				P2Set p2set = absHeap.get(pair);
				total += p2set.size();
				total_all += p2set.size();
				for (HeapObject hObj : p2set.getHeapObjects()) {
					BoolExpr cst = p2set.getConstraint(hObj);
					if (cst.BoolValue() == Z3_lbool.Z3_L_TRUE) {
						t++;
						t_all++;
					} else if (cst.BoolValue() == Z3_lbool.Z3_L_FALSE) {
						f++;
						f_all++;
					} else {
						other++;
						other_all++;
					}
				}
				b.append("----------------------------------------------\n");
				b.append("Method: " + m + "\n");
				b.append("Total: " + total + "\n");
				b.append("True cst: " + t + "\n");
				b.append("False cst: " + f + "\n");
				b.append("Other cst: " + other + "\n");
			}
		}

		b.append("----------------------------------------------\n");
		b.append("Total: " + total_all + "\n");
		b.append("True cst: " + t_all + "\n");
		b.append("False cst: " + f_all + "\n");
		b.append("Other cst: " + other_all + "\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + "statistics"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

}
