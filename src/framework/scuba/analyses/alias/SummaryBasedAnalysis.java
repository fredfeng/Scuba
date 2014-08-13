package framework.scuba.analyses.alias;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICG;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Pair;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.controller.SummaryController;
import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.Env;
import framework.scuba.domain.EpsilonFieldElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.LocalAccessPath;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.StaticAccessPath;
import framework.scuba.domain.SumConclusion;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.SummariesEnv.PropType;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;
import framework.scuba.helper.SCCHelper4CG;
import framework.scuba.helper.SCCHelper4Heap;
import framework.scuba.utils.ChordUtil;
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
	protected ProgramRel relPIM;
	protected ProgramRel relAppLocal;
	protected ProgramRel relDcLocal;
	protected ProgramRel relLibM;
	protected ProgramRel relVH;
	protected ProgramRel relMV;
	protected ProgramRel relVValias;
	protected ProgramRel relTF;
    protected CIPAAnalysis cipa;

	protected CICG callGraph;

	HashMap<Node, Set<jq_Method>> nodeToScc = new HashMap<Node, Set<jq_Method>>();
	HashMap<Set<jq_Method>, Node> sccToNode = new HashMap<Set<jq_Method>, Node>();
	HashMap<jq_Method, Node> methToNode = new HashMap<jq_Method, Node>();
	
	protected SummaryController sumController = new SummaryController();

	protected IntraProcSumAnalysis intrapro = new IntraProcSumAnalysis();
	
	private Set<jq_Method> libMeths;

	// total time spending on analyzing lib.
	public long libTime = 0;

	// total time spending on analyzing app.
	public long appTime = 0;

	private void init() {
		getCallGraph();

		//set up cipa.
		setupCipa();
		ChordUtil.checkEmptyPts(cipa);
		ChordUtil.checkEmptyFields(cipa);
		ChordUtil.populateTF(relTF);
		// app locals from haiyan's analysis.
		extractAppLocals();

		long start = System.nanoTime();
		// compute SCCs and their representative nodes.
		sumAnalyze();
		conclude();
		long end = System.nanoTime();
		StringUtil.reportSec("Sum-based analysis running time: ", start, end);

//		dumpCallGraph();

		// perform downcast analysis
//		new DowncastAnalysis(relDcm, relDVH, this).run();

		// perform points to set.
		//new P2SetComparison(relVH, relMV, this).run();
		//new MayAliasAnalysis(relMV, relVValias, this).run();
		//make sure you have result.txt under your work-dir before you turn on this!.
//		new RegressionAnalysis(this).run();
	}
	
	private void setupCipa() {
		cipa = (CIPAAnalysis)ClassicProject.g().getTask("cipa-java");
		assert cipa!=null;
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
	    int numF = domF.size();
	    //0 is null in chord.
		for (int v = 1; v < numF; v++) {
			jq_Field field = domF.get(v);
			if(!field.getType().isReferenceType())
				continue;
			//ignore static and public f.
			if (field.isPublic() || field.isStatic())
				continue;
			jq_Class clz = field.getDeclaringClass();
			//is this in app?
			if(!ChordUtil.isLibClass(clz))
				continue;
			
			Set<jq_Method> candidates = new HashSet<jq_Method>();
			if (field.isPrivate())
				for(int i = 0; i < clz.getDeclaredInstanceMethods().length; i++)
					candidates.add(clz.getDeclaredInstanceMethods()[i]);

			if (field.isProtected()) {
				// methods in the same package.
				for (jq_Reference ref : Program.g().getClasses()) {
					if (!(ref instanceof jq_Class))
						continue;
					jq_Class pakClz = (jq_Class) ref;
					if (!pakClz.isInSamePackage(clz))
						continue;
					
					for(int i = 0; i < pakClz.getDeclaredInstanceMethods().length; i++)
						candidates.add(pakClz.getDeclaredInstanceMethods()[i]);
				}
			}
			
			boolean unescape = true;
			for (Object o : candidates) {
				// will this field escape from this method?
				jq_Method meth = (jq_Method) o;
				//static method can not manipulate this.f
				assert !meth.isStatic();
				if(meth.isAbstract())
					continue;
				if(!SummariesEnv.v().getReachableMethods().contains(meth))
					continue;
				// is this method contains load operation of this field?
				if (!ChordUtil.hasLoadInst(meth, field))
					continue;
				// can this field escape from current method?
				if (ChordUtil.isEscapeFromMeth(cipa, meth, field)) {
					unescape = false;
					break;
				}
			}
			
			if (unescape) {
				Env.unescapeFields.add(field);
				System.out.println("unescape: " + field);
			}
		}
	}

	// pre-analysis to extract all locals in application. This will decide which
	// part of locals we need to propagate to the root level.
	private void extractAppLocals() {
		if (SummariesEnv.v().getLocalType() == PropType.APPLOCAL) {
			if (!relAppLocal.isOpen())
				relAppLocal.load();

			Iterable<Register> res = relAppLocal.getAry1ValTuples();
			Set<Register> propSet = SetUtils.iterableToSet(res,
					relAppLocal.size());
			SummariesEnv.v().addAllPropSet(propSet);
		} else if (SummariesEnv.v().getLocalType() == PropType.DOWNCAST) {
			if (!relDcLocal.isOpen())
				relDcLocal.load();

			Iterable<Register> res = relDcLocal.getAry1ValTuples();
			Set<Register> propSet = SetUtils.iterableToSet(res,
					relDcLocal.size());
			SummariesEnv.v().addAllPropSet(propSet);
		} else {
			if (!relAppLocal.isOpen())
				relAppLocal.load();

			Iterable<Register> res = relAppLocal.getAry1ValTuples();
			Set<Register> propSet = SetUtils.iterableToSet(res,
					relAppLocal.size());
			SummariesEnv.v().addAllPropSet(propSet);
		}
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
		
		int apNum = 0;
		int allocNum = 0;
		for(AbsMemLoc loc : mainSum.getAbsHeap().heap) {
			if(loc instanceof AccessPath) {
				apNum++;
				System.out.println("invalid: " + loc);
			}
			if(loc instanceof AllocElem) allocNum++;
		}
		
		System.out.println("total alloc: " + allocNum + " total ap: " + apNum );
	}

	private void sumAnalyze() {
		intrapro.setController(sumController);
		SummariesEnv.v().setController(sumController);
		
		// step 1: collapse scc into one node.
		Graph repGraph = collapseSCCs();

		if (G.tuning) {
			StringUtil.reportInfo("Total # of SCCs: "
					+ repGraph.getNodes().size());
			StringUtil.reportInfo("Total reachableM: " + relReachableM.size());
		}

		Set<Node> worklist = new LinkedHashSet<Node>();

		for (Node methNode : repGraph.getNodes())
			if ((methNode.getSuccessors().size() == 0)) {
				assert methNode != null : "Entry can not be null";
				worklist.add(methNode);
			}

		// foreach leaf in the callgraph. Add them to the worklist.
		Set<Node> visited = new HashSet<Node>();
		while (!worklist.isEmpty()) {
			Node worker = worklist.iterator().next();
			worklist.remove(worker);
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
	}

	/**
	 * Collapse SCCs and return the representative graph.
	 * 
	 * @return
	 */
	private Graph collapseSCCs() {
		Graph repGraph = new Graph();
		Set<jq_Method> sccs = new HashSet<jq_Method>();
		SCCHelper4CG s4g = new SCCHelper4CG(callGraph, callGraph.getRoots());
		int maxSize = 0;
		int tltSCCMeths = 0;
		int idx = 0;
		int methsInCG = callGraph.getNodes().size();

		for (Set<jq_Method> scc : s4g.getComponents()) {
			// create a representation node for each scc.
			idx++;
			tltSCCMeths += scc.size();
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

		assert tltSCCMeths == methsInCG : tltSCCMeths + " VS " + methsInCG;

		for (Set<jq_Method> scc : s4g.getComponents()) {
			Node cur = sccToNode.get(scc);
			for (jq_Method nb : scc) {
				// init successor.
				for (jq_Method sucb : callGraph.getSuccs(nb)) {
					if (scc.contains(sucb)) {
						if (scc.size() == 1 && nb.equals(sucb)) {
							cur.setSelfLoop(true);
						}
						continue;
					} else {
						Node scNode = methToNode.get(sucb);
						cur.addSuccessor(scNode);
					}
				}
				// init preds.
				for (jq_Method pred : callGraph.getPreds(nb)) {
					if (scc.contains(pred)) {
						if (scc.size() == 1 && nb.equals(pred)) {
							cur.setSelfLoop(true);
						}
						continue;
					} else {
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
			
			if(G.countScc >= 4633) {
				StringUtil.reportTotalTime("total in equal: ", G.equalsTime);
				StringUtil.reportTotalTime("total in union: ", G.unionTime);
				StringUtil.reportTotalTime("total in intersect: ", G.interTime);
				StringUtil.reportTotalTime("total in extract: ", G.extractTime);
				StringUtil.reportTotalTime("total in substitute: ", G.subTime);
				StringUtil.reportTotalTime("total in simplfy: ", G.simTime);
				StringUtil.reportTotalTime("total in genEqTime: ", G.genEqTime);
				StringUtil.reportTotalTime("total in cst1: ", G.cst1);
				StringUtil.reportTotalTime("total in cst2: ", G.cst2);
				StringUtil.reportTotalTime("total in cst3: ", G.cst3);
				StringUtil.reportTotalTime("total in cst4: ", G.cst4);

				StringUtil.reportTotalTime("total in instEqTime: ", G.eqTime);
				StringUtil.reportTotalTime("total in instGeTime: ", G.geTime);
				StringUtil.reportTotalTime("total in instLeTime: ", G.leTime);

				StringUtil.reportTotalTime("total in instSubTime: ", G.instSubTime);
				StringUtil.reportTotalTime("total in instLiftTime: ", G.liftTime);
				StringUtil.reportTotalTime("total in instExprTime: ", G.exprTime);
				StringUtil.reportTotalTime("total in inst: ", G.instTime);
				StringUtil.reportTotalTime("total in gencst: ", G.genTime);
				
			}
		}

		long startSCC = System.nanoTime();

		assert !node.isTerminated() : "Should not analyze a node twice.";

		// 1.get its corresponding scc
		Set<jq_Method> scc = nodeToScc.get(node);

		if (scc.size() == 1) {
			// self loop. perform scc.
			if (node.isSelfLoop()) {
				analyzeSCC(node);
			} else {
				jq_Method m = scc.iterator().next();
				analyze(m, false);
			}
		} else {
			analyzeSCC(node);
		}

		// at the end, mark it as terminated.
		terminate(node);

		if (G.tuning) {
			long endSCC = System.nanoTime();
			StringUtil.reportSec("Analyzing SCC in ", startSCC, endSCC);
		}
	}

	private void terminate(Node node) {
		node.setTerminated(true);

		// when terminating, decide what locations in the summary to propagate
		Set<jq_Method> scc = nodeToScc.get(node);

		// when terminating, clean up locals in the summary
		if (SummariesEnv.v().useClearLocals()) {
			for (jq_Method m : scc) {
				Summary sum = SummariesEnv.v().getSummary(m);
				if (m != null)
					sumController.removeLocals(sum);
			}
		}
	}

	// check whether all nodes have terminated.
	private boolean allTerminated(Set<Node> succs) {
		boolean flag = true;
		for (Node node : succs)
			if (!node.isTerminated()) {
				flag = false;
				break;
			}
		return flag;
	}

	private Pair<Boolean, Boolean> analyze(jq_Method m, boolean isBadScc) {
		long startMeth = System.nanoTime();

		Summary summary = SummariesEnv.v().initSummary(m);
		summary.setInBadScc(isBadScc);
		summary.setChanged(new Pair<Boolean, Boolean>(false, false));
		intrapro.setSummary(summary);

		String signature = m.toString();
		if (SummariesEnv.v().isStubMethod(signature))
			return new Pair<Boolean, Boolean>(false, false);

		ControlFlowGraph cfg = m.getCFG();

		intrapro.analyze(cfg);

		if (G.validate) {
			summary.validate();
		}

		summary.setHasAnalyzed();
		
		detectCycle(summary);

//		 summary.getAbsHeap().dumpHeapToFile(
//		 m.getDeclaringClass() + "@" + m.toString().replace("/", ""));

		int num = 0;
		for (Pair p : summary.getAbsHeap().keySet()) {
			num += summary.getAbsHeap().get(p).size();
		}
		
		if (num > 100) {
			int apNum = 0;
			int allocNum = 0;
			
			Pair<Integer, Integer> allocPair = getInOutInfo(
					summary.getAbsHeap(), AllocElem.class);
			for(AbsMemLoc loc : summary.getAbsHeap().heap) {
				if(loc instanceof AccessPath) apNum++;
				if(loc instanceof AllocElem) allocNum++;
			}
			StringUtil.reportInfo("dbgBlowup: "
					+ "------------------------------------------------");
			StringUtil.reportInfo("dbgBlowup: analyzing " + m);

			StringUtil.reportInfo("dbgBlowup: "
					+ " edges in the current caller: " + num);
			int localAP = 0;
			for(AbsMemLoc loc : summary.getAbsHeap().heap) {
				if(loc instanceof StaticAccessPath) {
					int alloc = 0;
					int gAp = 0;
					int lAp = 0;
					for (Pair<AbsMemLoc, FieldElem> pair : summary.getAbsHeap()
							.keySet()) {
						AbsMemLoc first = pair.val0;
						if (first.equals(loc)) {
							P2Set p2s = summary.getAbsHeap().get(pair);
							//what are those p2set looks like?
							for(HeapObject tgt : p2s.keySet()) {
								if(tgt instanceof AllocElem) 
									alloc++;
								if(tgt instanceof LocalAccessPath) {
									System.out.println("badglobal:" + loc + "-->" + tgt);
									lAp++;
								}
								if(tgt instanceof StaticAccessPath) 
									gAp++;
							}
						}
					}
					System.out.println("global accesspath:  alloc: " + alloc + " gAp: " + gAp + " lAp: " + lAp + " : " + loc);
				}
				
				if(loc instanceof LocalAccessPath) {
					localAP++;
					System.out.println("local ap: " + loc);
					for (Pair<AbsMemLoc, FieldElem> pair : summary.getAbsHeap()
							.keySet()) {
						AbsMemLoc first = pair.val0;
						if (first.equals(loc)) {
							P2Set p2s = summary.getAbsHeap().get(pair);
							//what are those p2set looks like?
							for(HeapObject tgt : p2s.keySet()) {
								if(tgt instanceof StaticAccessPath) {
									System.out.println("very bad: " + tgt);
								}
							}
						}
					}
				}
			}
			System.out.println("BAD..." + num + " " + m + " AP: " + apNum + "(" + localAP + ")"
					+ " alloc: " + allocNum + " alloc out Edge: "
					+ allocPair.val1 + " inEdge: " + allocPair.val0);
			System.out.println("--------------alloc---------------------");
			for(AbsMemLoc loc : summary.getAbsHeap().heap) {
				if(loc instanceof AllocElem) {
					boolean flag = true;
					for(Pair<AbsMemLoc, FieldElem> pair : summary.getAbsHeap().keySet()) {
						AbsMemLoc first = pair.val0;
						P2Set p2s = summary.getAbsHeap().get(pair);
						if (p2s.keySet().contains(loc)) {
							if (!(first instanceof LocalVarElem)) {
								flag = false;
								break;
							}
						}
					}
					System.out.println("loc allocElem:  " + flag + " " + loc);
				}
			}

			StringUtil.reportInfo("dbgBlowup: "
					+ "~~~~~~~~~~~~~~~~dbgBlowup info~~~~~~~~~~~~~~~~~~~~");
		}

		long endMeth = System.nanoTime();
		long delta = endMeth - startMeth;
		if (libMeths.contains(m)) {
			libTime += delta;
		} else {
			appTime += delta;
		}

		return summary.isChanged();
	}
	
	private Pair<Integer, Integer> getInOutInfo(AbstractHeap heap, Class clz) {
		int in = 0;
		int out = 0;
		for(Pair<AbsMemLoc, FieldElem> pair : heap.keySet()) {
			AbsMemLoc first = pair.val0;
			P2Set p2s = heap.get(pair);
			
			if(first.getClass().equals(clz)) {
				out += heap.get(pair).size();
			}
			
			for (AbsMemLoc tgt : p2s.keySet())
				if (tgt.getClass().equals(clz))
					in++;
		}
		Pair<Integer, Integer> infopair = new Pair(in, out);
		return infopair;
	}

	private void analyzeSCC(Node node) {
		Set<jq_Method> scc = nodeToScc.get(node);

		// Set<jq_Method> wl = new HashSet<jq_Method>();
		Set<jq_Method> wl = new LinkedHashSet<jq_Method>();
		// add all methods to worklist
		wl.addAll(scc);

		while (!wl.isEmpty()) {
			jq_Method worker = wl.iterator().next();
			wl.remove(worker);

			if (G.tuning)
				StringUtil.reportInfo("SCC counter: " + wl.size() + ":"
						+ worker);
			

			Pair<Boolean, Boolean> changed = analyze(worker,
					(scc.size() > SummariesEnv.v().sccLimit));

			// only when changing the summary, we add all the callers
			if (changed.val1) {
				Set<jq_Method> bak = new LinkedHashSet<jq_Method>();
				for (jq_Method pred : callGraph.getPreds(worker))
					if (scc.contains(pred))
						bak.add(pred);
				bak.addAll(wl);
				wl = new LinkedHashSet<jq_Method>(bak);
			}
		}
	}
	
	protected void detectCycle(Summary sum) {
		System.out.println("Detecting cycle for " + sum.getMethod());
		int heapNum = sum.getAbsHeap().getAllMemLocs().size();
		SCCHelper4Heap s4g = new SCCHelper4Heap(sum.getAbsHeap(), sum.getAbsHeap().getAllMemLocs());
		int sccEdges = 0;
		int sccAp = 0;

		for(Set<AbsMemLoc> scc : s4g.getComponents()) {
			if(scc.size() > 1) {
//				Iterator<AbsMemLoc> it = scc.iterator();
//				System.out.println("Detecting cycle SCC size: " + scc.size() + " Total: " + heapNum);
//				while(it.hasNext()) {
//					AbsMemLoc loc = it.next();
//					loc.setInCycle(true);
//				}
				for(AbsMemLoc apLoc : scc) {
					apLoc.setInCycle(true);
					if(apLoc instanceof AccessPath)
						sccAp++;
				}
				//how many edges are in this scc?
				for (Pair<AbsMemLoc, FieldElem> pair : sum.getAbsHeap().locToP2Set.keySet()) {
					AbsMemLoc src = pair.val0;
					P2Set tgts = sum.getAbsHeap().locToP2Set.get(pair);
					if(scc.contains(src)) {
						src.setInCycle(true);
						for(HeapObject o : tgts.keySet()) 
							sccEdges++;
					} else {
						for(HeapObject o : tgts.keySet()) 
							if(scc.contains(o))
								sccEdges++;
					}
				}
			} else {
				AbsMemLoc loc = scc.iterator().next();
				for (Pair<AbsMemLoc, FieldElem> pair : sum.getAbsHeap().locToP2Set.keySet()) {
					if(pair.val0.equals(loc)) {
						P2Set tgts = sum.getAbsHeap().locToP2Set.get(pair);
						if(tgts.keySet().contains(loc)) {
							loc.setInCycle(true);
							break;
						}
					}
				}

			}
		}
		
		/*int totalEdges = 0;
		int totalAP = 0;
		int totalAlloc2Ap = 0;
		int totalAp2Ap = 0;

		for (Pair<AbsMemLoc, FieldElem> pair : sum.getAbsHeap().locToP2Set.keySet()) {
			AbsMemLoc src = pair.val0;
			P2Set tgts = sum.getAbsHeap().locToP2Set.get(pair);
			totalEdges += tgts.keySet().size();
			
			for(HeapObject o : tgts.keySet()) {
				if ((src instanceof AccessPath) && (o instanceof AccessPath))
					totalAP++;
			
				if ((src instanceof AllocElem) && (o instanceof AccessPath))
					totalAlloc2Ap++;
				
				if ((src instanceof AccessPath) && (o instanceof AccessPath))
					totalAp2Ap++;
			}
						
		}
		
		if(totalEdges > 0 && totalAP > 0 && sccEdges > 0)
			System.out.println("Detecting cycle info: total: " + totalEdges
					+ " scc: " + sccEdges + " percentage: " + sccEdges * 100
					/ totalEdges + "%" + " | AP in SCC: " + sccAp * 100
					/ sccEdges + "%" + " | AP in total: " + sccAp * 100 / totalAP
					+ "%");
		
		if(totalEdges > 0 && totalAlloc2Ap > 0)
			System.out.println("Detecting alloc2ap: " + totalAlloc2Ap * 100
					/ totalEdges + "%");
		
		if(totalEdges > 0 && totalAp2Ap > 0)
			System.out.println("Detecting ap2ap: " + totalAp2Ap + " " + totalAp2Ap * 100
					/ totalEdges + "%");*/
		
		System.out.println("End cycle detection......." + sccEdges + " AP in scc: " + sccAp);
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
		relPIM = (ProgramRel) ClassicProject.g().getTrgt("PIM");
		relAppLocal = (ProgramRel) ClassicProject.g().getTrgt("AppLocal");
		relDcLocal = (ProgramRel) ClassicProject.g().getTrgt("DcLocal");
		relLibM = (ProgramRel) ClassicProject.g().getTrgt("librariesM");
		relMV = (ProgramRel) ClassicProject.g().getTrgt("MV");
		relVH = (ProgramRel) ClassicProject.g().getTrgt("ptsVH");
		relVValias = (ProgramRel) ClassicProject.g().getTrgt("VValias");
		
	    relTF = (ProgramRel) ClassicProject.g().getTrgt("TF");

		if (!relDcLocal.isOpen())
			relDcLocal.load();

		if (!relReachableM.isOpen())
			relReachableM.load();
		
		if (!relTF.isOpen())
			relTF.load();
		
		Iterable<jq_Method> resM = relReachableM.getAry1ValTuples();
		Set<jq_Method> reaches = SetUtils.iterableToSet(resM,
				relReachableM.size());
		SummariesEnv.v().setReachableMethods(reaches);

		if (!relLibM.isOpen())
			relLibM.load();

		Iterable<jq_Method> res = relLibM.getAry1ValTuples();
		libMeths = SetUtils.iterableToSet(res, relLibM.size());
		SummariesEnv.v().setLibMeths(libMeths);

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

	public Set<AllocElem> query(jq_Class clazz, jq_Method method,
			Register variable) {
		Set<AllocElem> ret = new HashSet<AllocElem>();
		SumConclusion sum = SummariesEnv.v().getFinalSum();
		// Summary sum = SummariesEnv.v().getSummary(entry);
		assert (sum != null) : "the entry method should have a summary!";
		Summary sum1 = SummariesEnv.v().getSummary(method);

		if (sum1 == null) {

			StringUtil.reportInfo("[dbgAntlr]: "
					+ "[we cannot get the summary for the method!] " + method);

			return ret;
		}

		assert (sum1 != null) : "the method of the variable should have a summary!";

		if (G.dbgAntlr) {
			StringUtil.reportInfo("[dbgAntlr]" + " size of sum: "
					+ sum1.getHeapSize());
			StringUtil.reportInfo("[dbgAntlr]" + " variable method: " + method
					+ " Id: [" + G.IdMapping.get(sum1) + " ]");
		}

		LocalVarElem local = sum1.getAbsHeap().getLocalVarElem4Query(clazz,
				method, variable);

		if (local == null) {

			StringUtil.reportInfo("[dbgAntlr] "
					+ "[we cannot get the location in the heap!]");

		} else {

			StringUtil.reportInfo("[dbgAntlr] "
					+ "[we can get the location in the heap!]");

			Set<FieldElem> fields = local.getFields();
			assert (fields.size() == 1) : "local can only have 1 field (epsilon)!";
			FieldElem f = fields.iterator().next();
			assert (f instanceof EpsilonFieldElem) : "local can only have epsilon field!";
			EpsilonFieldElem e = (EpsilonFieldElem) f;
			StringUtil.reportInfo("[dbgAntlr] "
					+ "[P2Set in the declearing method]");
			P2Set p2set = sum1.getAbsHeap().getHeap()
					.get(new Pair<AbsMemLoc, FieldElem>(local, e));
			StringUtil
					.reportInfo("[dbgAntlr] "
							+ "------------------------------------------------------------------------------------");
			for (HeapObject hObj : p2set.keySet()) {
				StringUtil.reportInfo("[dbgAntlr] " + hObj);
			}
			StringUtil
					.reportInfo("[dbgAntlr] "
							+ "------------------------------------------------------------------------------------");

			ret = sum.getP2Set(local, e);
		}

		return ret;
	}
}
