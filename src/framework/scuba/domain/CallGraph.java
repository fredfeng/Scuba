package framework.scuba.domain;

import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.bddbddb.Rel.RelView;
import chord.project.analyses.ProgramRel;
import chord.util.ArraySet;
import chord.util.SetUtils;
import chord.util.graph.AbstractGraph;

/**
 * Implementation of a context-insensitive call graph.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CallGraph extends AbstractGraph<jq_Method> implements ICICG {
	private DomM domM;
	private ProgramRel relRootM;
	private ProgramRel relReachableM;
	private ProgramRel relPIM;
	private ProgramRel relPMM;

	public CallGraph(DomM domM, ProgramRel relRootM, ProgramRel relReachableM,
			ProgramRel relPIM, ProgramRel relPMM) {
		this.domM = domM;
		this.relRootM = relRootM;
		this.relReachableM = relReachableM;
		this.relPIM = relPIM;
		this.relPMM = relPMM;
	}

	public Set<Quad> getCallers(jq_Method meth) {
		if (!relPIM.isOpen())
			relPIM.load();
		RelView view = relPIM.getView();
		view.selectAndDelete(1, meth);
		Iterable<Quad> res = view.getAry1ValTuples();
		Set<Quad> invks = SetUtils.newSet(view.size());
		for (Quad invk : res)
			invks.add(invk);
		return invks;
	}

	public Set<jq_Method> getTargets(Quad invk) {
		if (!relPIM.isOpen())
			relPIM.load();
		RelView view = relPIM.getView();
		view.selectAndDelete(0, invk);
		Iterable<jq_Method> res = view.getAry1ValTuples();
		Set<jq_Method> meths = SetUtils.newSet(view.size());
		for (jq_Method meth : res)
			meths.add(meth);
		return meths;
	}

	public int numRoots() {
		if (!relRootM.isOpen())
			relRootM.load();
		return relRootM.size();
	}

	public int numNodes() {
		if (!relReachableM.isOpen())
			relReachableM.load();
		return relReachableM.size();
	}

	public int numPreds(jq_Method node) {
		throw new UnsupportedOperationException();
	}

	public int numSuccs(jq_Method node) {
		throw new UnsupportedOperationException();
	}

	public Set<jq_Method> getRoots() {
		if (!relRootM.isOpen())
			relRootM.load();
		Iterable<jq_Method> res = relRootM.getAry1ValTuples();
		return SetUtils.iterableToSet(res, relRootM.size());
	}

	public Set<jq_Method> getNodes() {
		if (!relReachableM.isOpen())
			relReachableM.load();
		Iterable<jq_Method> res = relReachableM.getAry1ValTuples();
		return SetUtils.iterableToSet(res, relReachableM.size());
	}

	public Set<jq_Method> getPreds(jq_Method meth) {
		if (!relPMM.isOpen())
			relPMM.load();
		RelView view = relPMM.getView();
		view.selectAndDelete(1, meth);
		Iterable<jq_Method> res = view.getAry1ValTuples();
		return SetUtils.iterableToSet(res, view.size());
	}

	public Set<jq_Method> getSuccs(jq_Method meth) {
		if (!relPMM.isOpen())
			relPMM.load();
		RelView view = relPMM.getView();
		view.selectAndDelete(0, meth);
		Iterable<jq_Method> res = view.getAry1ValTuples();
		return SetUtils.iterableToSet(res, view.size());
	}

	public Set<Quad> getLabels(jq_Method srcMeth, jq_Method dstMeth) {
		Set<Quad> invks = new ArraySet<Quad>();
		ControlFlowGraph cfg = srcMeth.getCFG();
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (op instanceof Invoke && calls(q, dstMeth))
					invks.add(q);
			}
		}
		return invks;
	}

	public boolean calls(Quad invk, jq_Method meth) {
		if (!relPIM.isOpen())
			relPIM.load();
		return relPIM.contains(invk, meth);
	}

	public boolean hasRoot(jq_Method meth) {
		return domM.indexOf(meth) == 0;
	}

	public boolean hasNode(jq_Method meth) {
		if (!relReachableM.isOpen())
			relReachableM.load();
		return relReachableM.contains(meth);
	}

	public boolean hasEdge(jq_Method meth1, jq_Method meth2) {
		if (!relPMM.isOpen())
			relPMM.load();
		return relPMM.contains(meth1, meth2);
	}

	/**
	 * Frees relations used by this call graph if they are in memory.
	 * <p>
	 * This method must be called after clients are done exercising the
	 * interface of this call graph.
	 */
	public void free() {
		if (relRootM.isOpen())
			relRootM.close();
		if (relReachableM.isOpen())
			relReachableM.close();
		if (relPIM.isOpen())
			relPIM.close();
		if (relPMM.isOpen())
			relPMM.close();
	}
}
