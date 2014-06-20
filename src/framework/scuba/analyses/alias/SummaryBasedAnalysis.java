package framework.scuba.analyses.alias;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.bddbddb.Rel.RelView;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.CallGraph;
import framework.scuba.domain.Env;
import framework.scuba.domain.EpsilonFieldElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.SumConclusion;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.G;
import framework.scuba.helper.SCCHelper4CG;
import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;
import framework.scuba.utils.StringUtil;

/**
 * Summary-based analysis. 1. Build and get a CHA-based CallGraph. 2. Compute
 * SCC 3. Run the worklist algorithm author: Yu Feng email: yufeng@cs.utexas.edu
 */

@Chord(name = "sum-java", consumes = { "rootM", "reachableM", "IM", "MM",
		"cha", "dcm" })
public class SummaryBasedAnalysis extends JavaAnalysis {

	protected DomM domM;
	protected ProgramRel relRootM;
	protected ProgramRel relReachableM;
	protected ProgramRel relIM;
	protected ProgramRel relMM;
	protected ProgramRel relCHA;
	protected ProgramRel relDcm;
	protected ProgramRel relDVH;
	protected ProgramRel relPMM;

	protected CallGraph callGraph;

	HashMap<Node, Set<jq_Method>> nodeToScc = new HashMap<Node, Set<jq_Method>>();
	HashMap<Set<jq_Method>, Node> sccToNode = new HashMap<Set<jq_Method>, Node>();
	HashMap<jq_Method, Node> methToNode = new HashMap<jq_Method, Node>();

	List<jq_Method> accessSeq = new LinkedList<jq_Method>();

	IntraProcSumAnalysis intrapro = new IntraProcSumAnalysis();

	private void init() {
		getCallGraph();
		
		// compute SCCs and their representative nodes.
		sumAnalyze();

		conclude();

		// dump interesting stats
		dumpStatistics();

		// perform downcast analysis
		downcast();

		StringUtil.reportInfo("[Scuba] summaries size: "
				+ SummariesEnv.v().getSums().keySet().size());

		for (jq_Method meth : SummariesEnv.v().getSums().keySet())
			StringUtil.reportInfo("[Scuba] Summaries: " + meth);

	}

	// summarize all heaps.
	private void conclude() {
		Set<AbstractHeap> clinitHeaps = new HashSet<AbstractHeap>();
		for (jq_Method meth : callGraph.getRoots()) {
			if (!Program.g().getMainMethod().equals(meth)) {
				Summary clinitSum = SummariesEnv.v().getSummary(meth);
				clinitHeaps.add(clinitSum.getAbsHeap());
			}
		}

		Summary mainSum = SummariesEnv.v().getSummary(
				Program.g().getMainMethod());
		SummariesEnv.v().sumAll(clinitHeaps, mainSum.getAbsHeap());
	}

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
				if (G.tuning) {
					StringUtil.reportInfo("Before work on " + worker);
				}

				workOn(worker);

				visited.add(worker);
			} else {
				// append worker to the end of the List.class
				worklist.add(worker);
			}

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

		if (G.dbgQuery) {
			for (jq_Method m : scc) {
				StringUtil.reportInfo("Byte code for Method: [" + G.countScc
						+ "]" + m);
				StringUtil.reportInfo(m.getCFG().fullDump());
			}
		}

		if (scc.size() == 1) {
			// self loop. perform scc.
			if (node.getSuccessors().contains(node)) {
				analyzeSCC(node);
			} else {
				jq_Method m = scc.iterator().next();
				analyze(m);
				if (G.dbgPermission) {
					StringUtil.reportInfo("Evils:[" + G.countScc
							+ "] begin regular node");
					Summary sum = SummariesEnv.v().getSummary(m);
					if (sum != null) {
						int i = sum.getHeapSize();
						StringUtil.reportInfo("Evils:[" + G.countScc
								+ "] size [" + i + "]" + " method: " + m);
					} else {
						StringUtil.reportInfo("Evils:[" + G.countScc
								+ "] no IR" + " method: " + m);
					}
				}

				if (G.dbgQuery) {

					if (G.countScc == G.sample) {
						SummariesEnv.v().getSummary(m)
								.dumpSummaryToFile("" + G.sample);
						SummariesEnv.v().getSummary(m)
								.dumpSummaryMappingToFile("" + G.sample);
					}
				}
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
	}

	// mark current node as terminated and perform GC on its successor, if
	// possible.
	private void terminateAndDoGC(Node node) {
		node.setTerminated(true);

		// when terminating, clean up locals in the summary
		if (SummariesEnv.v().cleanLocals()) {
			Set<jq_Method> scc = nodeToScc.get(node);
			for (jq_Method m : scc) {
				Summary sum = SummariesEnv.v().getSummary(m);
				if (m != null) {
					sum.removeLocals();
				}
			}
		}

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

	private Pair<Boolean, Boolean> analyze(jq_Method m) {
		accessSeq.add(m);

		Summary summary = SummariesEnv.v().initSummary(m);
		summary.setChanged(new Pair<Boolean, Boolean>(false, false));
		intrapro.setSummary(summary);

		// check blacklist methods.
		if (SummariesEnv.v().openBlklist()
				&& SummariesEnv.v().isInBlacklist(m.toString())) {
			StringUtil.reportInfo("add to blacklist: " + m.toString());
			return new Pair<Boolean, Boolean>(false, false);
		}

		// ControlFlowGraph cfg = CodeCache.getCode(m);
		ControlFlowGraph cfg = m.getCFG();

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

		if (G.seePerf) {
			if (m.toString().equals("hashCode:()I@java.util.Hashtable$Entry")) {
				summary.dumpSummaryToFile("$hashCode");
			}
		}
		
		return summary.isChanged();
	}
	
	public static int cgProgress = 0;
	public static boolean inS = false;

	private void analyzeSCC(Node node) {
		inS = true;
		Set<jq_Method> scc = nodeToScc.get(node);

		if (G.dbgPermission) {
			StringUtil.reportInfo("Evils:[" + G.countScc + "] begin SCC");
			Map<Integer, Set<jq_Method>> evils = new TreeMap<Integer, Set<jq_Method>>();
			for (jq_Method m : scc) {
				Summary sum = SummariesEnv.v().getSummary(m);
				if (sum == null) {
					continue;
				}
				Set<jq_Method> s = evils.get(sum.getHeapSize());
				if (s == null) {
					s = new HashSet<jq_Method>();
					evils.put(sum.getHeapSize(), s);
				}
			}
			for (int i : evils.keySet()) {
				Set<jq_Method> s = evils.get(i);
				for (jq_Method m : s) {
					StringUtil.reportInfo("Evils:[" + G.countScc + "]size ["
							+ i + "]" + " method: " + m);
				}
			}
		}

		// Set<jq_Method> wl = new HashSet<jq_Method>();
		Set<jq_Method> wl = new LinkedHashSet<jq_Method>();
		// add all methods to worklist
		wl.addAll(scc);

		int times = 0;

		while (!wl.isEmpty()) {

			times++;

			if (G.dbgPermission) {
				StringUtil.reportInfo("Evils:[" + G.countScc
						+ "] begin Iteration [" + times + "]");
				Map<Integer, Set<jq_Method>> evils = new TreeMap<Integer, Set<jq_Method>>();
				for (jq_Method m : scc) {
					Summary sum = SummariesEnv.v().getSummary(m);
					if (sum == null) {
						continue;
					}
					Set<jq_Method> s = evils.get(sum.getHeapSize());
					if (s == null) {
						s = new HashSet<jq_Method>();
						evils.put(sum.getHeapSize(), s);
					}
					s.add(m);
				}
				for (int i : evils.keySet()) {
					Set<jq_Method> s = evils.get(i);
					for (jq_Method m : s) {
						StringUtil.reportInfo("Evils:[" + G.countScc
								+ "] size [" + i + "]" + " method: " + m);
					}

				}
			}

			jq_Method worker = wl.iterator().next();
			wl.remove(worker);

			if (G.tuning)
				StringUtil.reportInfo("SCC counter: " + wl.size() + ":"
						+ worker);

			Pair<Boolean, Boolean> changed = analyze(worker);

			// only when changing the summary, we add all the callers
			if (changed.val1) {
				for (jq_Method pred : callGraph.getPreds(worker))
					if (scc.contains(pred))
						wl.add(pred);
			}

		}
	}

	/* This method will be invoked by Chord automatically. */
	public void run() {
		domM = (DomM) ClassicProject.g().getTrgt("M");
		relRootM = (ProgramRel) ClassicProject.g().getTrgt("rootM");
		relReachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
		relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relMM = (ProgramRel) ClassicProject.g().getTrgt("MM");
		relCHA = (ProgramRel) ClassicProject.g().getTrgt("cha");
		relDcm = (ProgramRel) ClassicProject.g().getTrgt("dcm");
		relDVH = (ProgramRel) ClassicProject.g().getTrgt("dcmVH");
		relPMM = (ProgramRel) ClassicProject.g().getTrgt("PMM");

		// pass relCha ref to SummariesEnv
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
			callGraph = new CallGraph(domM, relRootM, relReachableM, relIM,
					relPMM);
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

	public static int count = 0;
	public static int me = 0;

	public Set<AllocElem> query(jq_Class clazz, jq_Method method,
			Register variable) {
		Set<AllocElem> ret = new HashSet<AllocElem>();
		jq_Method entry = Program.g().getMainMethod();
		SumConclusion sum = SummariesEnv.v().getFinalSum();
		// Summary sum = SummariesEnv.v().getSummary(entry);
		if (G.dbgQuery) {
			StringUtil.reportInfo("Query: "
					+ "---------------------------------------------");
			StringUtil.reportInfo("Query: " + " size of sum: "
					+ sum.getHeapSize());
			StringUtil.reportInfo("Query: " + " entry method: " + entry);
		}
		assert (sum != null) : "the entry method should have a summary!";
		Summary sum1 = SummariesEnv.v().getSummary(method);
		if (sum1 == null) {
			if (G.dbgQuery) {
				StringUtil.reportInfo("Query: "
						+ "[we cannot get the summary for the method!] "
						+ method);
			}
			return ret;
		}
		assert (sum1 != null) : "the method of the variable should have a summary!";
		if (G.dbgQuery) {
			StringUtil.reportInfo("Query: " + " size of sum: "
					+ sum1.getHeapSize());
			StringUtil.reportInfo("Query: " + " variable method: " + method);
		}
		if (sum1.getAbsHeap()
				.getHeap()
				.containsKey(
						new Pair<AbsMemLoc, FieldElem>(new LocalVarElem(clazz,
								method, variable), EpsilonFieldElem
								.getEpsilonFieldElem()))) {
			count++;
			if (G.dbgQuery) {
				StringUtil.reportInfo("Query: "
						+ "[we can get the location in the heap!]");

				StringUtil.reportInfo("Query: "
						+ "[P2Set in the declearing method]"
						+ sum1.getAbsHeap()
								.getHeap()
								.get(new Pair<AbsMemLoc, FieldElem>(
										new LocalVarElem(clazz, method,
												variable), EpsilonFieldElem
												.getEpsilonFieldElem())));
			}
		} else {
			if (G.dbgQuery) {
				StringUtil.reportInfo("Query: "
						+ "[we cannot get the location in the heap!]");
				sum1.dumpSummaryToFile("" + me++);
			}
			return ret;
		}
		LocalVarElem local = sum1.getLocalVarElem(clazz, method, variable);
		ret = sum.getP2Set(local);
		return ret;
	}

	// downcast analysis.
	public void downcast() {
		if (!relDcm.isOpen())
			relDcm.load();

		if (!relDVH.isOpen())
			relDVH.load();

		RelView view = relDcm.getView();
		Iterable<Trio<jq_Method, Register, jq_Type>> res = view
				.getAry3ValTuples();

		StringUtil.reportInfo("Number of downcast: " + relDcm.size());
		for (Trio<jq_Method, Register, jq_Type> trio : res) {
			jq_Method meth = trio.val0;
			Register r = trio.val1;
			// System.out.println(meth + " reg: " + r + " Type: " + trio.val2);
			Set<AllocElem> p2Set = query(meth.getDeclaringClass(), meth, r);

			Set<Quad> sites = new HashSet<Quad>();
			for (AllocElem alloc : p2Set) {
				sites.add(alloc.getAlloc().getAllocSite());
			}

			StringUtil.reportInfo("[Scuba] method: " + meth);
			StringUtil.reportInfo("[Scuba] p2Set of " + r + ":" + sites);

			// p2set of r in chord.
			RelView viewChord = relDVH.getView();
			viewChord.selectAndDelete(0, r);
			Iterable<Quad> resChord = viewChord.getAry1ValTuples();
			Set<Quad> pts = SetUtils.newSet(viewChord.size());
			// no filter, add all
			for (Quad inst : resChord)
				pts.add(inst);

			StringUtil.reportInfo("[Chord] p2Set of " + r + ":" + pts);

		}

		if (G.dbgQuery) {
			StringUtil.reportInfo("Query: " + count);
		}

	}

	public void dumpStatistics() {
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
			Map<Pair<AbsMemLoc, FieldElem>, P2Set> absHeap = sums.get(m)
					.getAbsHeap().getHeap();
			for (Pair<AbsMemLoc, FieldElem> pair : absHeap.keySet()) {
				P2Set p2set = absHeap.get(pair);
				total += p2set.size();
				total_all += p2set.size();
				for (HeapObject hObj : p2set.keySet()) {
					BoolExpr cst = p2set.get(hObj);
					if (ConstraintManager.isTrue(cst)) {
						t++;
						t_all++;
					} else if (ConstraintManager.isFalse(cst)) {
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

		System.out.println(b.toString());
		System.out.println("ALOADS------------" + Summary.aloadCnt);
		System.out.println("ASTORES------------" + Summary.astoreCnt);
		System.out.println("Array------------" + Summary.aNewArrayCnt);
		System.out.println("MultiArray------------" + Summary.aNewMulArrayCnt);
		System.out.println("Total downcast------------" + Summary.castCnt);
	}

}
