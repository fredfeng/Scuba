package framework.scuba.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AbstractHeap.VariableType;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.Env;
import framework.scuba.domain.EpsilonFieldElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.MemLocInstnItem;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.StackObject;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.utils.StringUtil;

public class SummaryController {
	
	// is this a param or local. helper function.
	public VariableType getVarType(jq_Method meth, Register r) {
		VariableType vt = VariableType.LOCAL_VARIABLE;

		ControlFlowGraph cfg = meth.getCFG();
		RegisterFactory rf = cfg.getRegisterFactory();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			Register v = rf.get(zIdx);
			if (v.equals(r)) {
				vt = VariableType.PARAMEMTER;
				break;
			}
		}
		return vt;
	}

	public StackObject getMemLocation(jq_Class clz, jq_Method meth, Register r,
			jq_Type type, Summary sum) {
		AbstractHeap absHeap = sum.getAbsHeap();
		VariableType vt = getVarType(meth, r);
		if (vt == VariableType.LOCAL_VARIABLE) {
			return absHeap.getLocalVarElem(clz, meth, r, type);
		} else if (vt == VariableType.PARAMEMTER) {
			return absHeap.getParamElem(clz, meth, r, type);
		}
		return null;
	}
	
	/**
	 * Given a specific method and access path o, return its constraint.
	 * 
	 * @return
	 */
	public BoolExpr genCst(P2Set p2Set, jq_Method callee, jq_Class statT,
			Set<jq_Method> tgtSet) {
		if (SummariesEnv.v().disableCst())
			return ConstraintManager.genTrue();
		// 1. Base case: No subtype of T override m: type(o) <= T
		if (!overrideByAnySubclass(callee, statT, tgtSet)) {
			if (statT.getSubClasses().length == 0)
				return ConstraintManager.hasEqType(p2Set, statT);
			else
				return ConstraintManager.hasIntervalType(p2Set, statT);

		} else {
			// 2. Inductive case: for each its *direct* subclasses that
			// do not override current method, call genCst recursively.
			BoolExpr t = ConstraintManager.genFalse();
			for (jq_Class sub : Env.getSuccessors(statT)) {
				jq_Method m = sub.getVirtualMethod(callee.getNameAndDesc());
				if (m != null && m.getDeclaringClass().equals(sub)) {
					continue;
				}
				BoolExpr phi = genCst(p2Set, callee, sub, tgtSet);
				t = ConstraintManager.union(t, phi);
			}
			// do the union.
			return ConstraintManager.union(t,
					ConstraintManager.hasEqType(p2Set, statT));
		}
	}

	/**
	 * Check whether given method is override by any of its subclasses.
	 * 
	 * @param callee
	 * @param statT
	 * @param tgt
	 * @return
	 */
	protected boolean overrideByAnySubclass(jq_Method callee, jq_Class statT,
			Set<jq_Method> tgtSet) {

		for (jq_Method tgt : tgtSet) {
			if (tgt.equals(callee))
				continue;
			jq_Class dyClz = tgt.getDeclaringClass();
			if (dyClz.extendsClass(statT))
				return true;
		}
		return false;
	}
	
	public Map<MemLocInstnItem, Set<AccessPath>> addToDepMap(
			Pair<AbsMemLoc, FieldElem> pair,
			Pair<MemLocInstnItem, Set<AccessPath>> deps, Summary sum) {
		return sum.getLocDepMap().add(pair, deps);
	}

	public void removeLocals(Summary sum) {
		AbstractHeap absHeap = sum.getAbsHeap();
		Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = absHeap.locToP2Set
				.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it.next();
			Pair<AbsMemLoc, FieldElem> pair = entry.getKey();
			AbsMemLoc loc = pair.val0;
			if (loc instanceof LocalVarElem) {
				absHeap.locToP2Set.remove(pair);
			}
		}
	}

	public boolean containsInstnedEdge(MemLocInstnItem item, AbsMemLoc src,
			HeapObject tgt, FieldElem f, Summary sum) {
		Set<Trio<AbsMemLoc, HeapObject, FieldElem>> set = sum.getInstnedEdges()
				.get(item);
		if (set == null) {
			return false;
		}
		return set.contains(new Trio<AbsMemLoc, HeapObject, FieldElem>(src,
				tgt, f));
	}

	public Set<Trio<AbsMemLoc, HeapObject, FieldElem>> getDepEdges(
			MemLocInstnItem item, AbsMemLoc loc, Summary sum) {
		Map<AbsMemLoc, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>> map = sum
				.getEdgeDepMap().get(item);
		if (map == null) {
			return null;
		}
		return map.get(loc);
	}

	public void removeAllInstnedEdges(MemLocInstnItem item,
			Set<Trio<AbsMemLoc, HeapObject, FieldElem>> edges, Summary sum) {
		Set<Trio<AbsMemLoc, HeapObject, FieldElem>> set = sum.getInstnedEdges()
				.get(item);
		if (set != null) {
			set.removeAll(edges);
		}
	}
	
//	public Set<AllocElem> getP2Set(LocalVarElem local) {
//		Set<AllocElem> ret = new HashSet<AllocElem>();
//		assert (local != null);
//		P2Set p2set = absHeap.locToP2Set.get(new Pair<AbsMemLoc, FieldElem>(
//				local, EpsilonFieldElem.getEpsilonFieldElem()));
//		if (p2set == null) {
//			return ret;
//		}
//		for (HeapObject hObj : p2set.keySet()) {
//			if (hObj instanceof AllocElem) {
//				ret.add((AllocElem) hObj);
//			} else {
//				assert (hObj instanceof StaticAccessPath)
//						&& (hObj.findRoot() instanceof StaticElem) : ""
//						+ "only StaticElem AccessPath allowed in the entry!";
//				AccessPathHelper.resolve(absHeap, (StaticAccessPath) hObj, ret);
//			}
//		}
//		return ret;
//	}
	
	// given a call site in the caller, return all the possible callee's
	// summaries and the corresponding constraints as a list of pairs
	// if no callee available, return ret (size == 0)
	public List<Pair<Summary, BoolExpr>> getSumCstPairList(Quad callsite, Summary sum) {
		jq_Method caller = callsite.getMethod();
		jq_Class clz = caller.getDeclaringClass();
		BoolExpr cst = ConstraintManager.genTrue();
		Operator opr = callsite.getOperator();
		List<Pair<Summary, BoolExpr>> ret = new ArrayList<Pair<Summary, BoolExpr>>();
		// find all qualified callees and the constraints
		// all dynamic targets.
		Set<jq_Method> tgtSet = Env.cg.getTargets(callsite);
		if (tgtSet.size() == 0) {
			StringUtil
					.reportInfo("Fail to find model for callee..." + callsite);
			return ret;
		}

		// include stativinvoke, vitualinvoke with one target.
		if (tgtSet.size() == 1) {
			jq_Method callee = tgtSet.iterator().next();
			Summary calleeSum = SummariesEnv.v().getSummary(callee);

			String signature = callee.toString();
			if (SummariesEnv.v().isStubMethod(signature))
				return ret;

			if (calleeSum == null) {
				StringUtil.reportInfo("Missing model for " + callee);
				return ret;
			}
			// assert calleeSum != null : "CalleeSum can not be null" +
			// callsite;
			if (calleeSum.hasAnalyzed()) {
				ret.add(new Pair<Summary, BoolExpr>(calleeSum, cst));
				return ret;
			}
		}

		if ((opr instanceof InvokeVirtual) || (opr instanceof InvokeInterface)) {
			RegisterOperand ro = Invoke.getParam(callsite, 0);

			if (ro.getType() instanceof jq_Array) {
				StringUtil.reportInfo("Do not handle array for " + callsite);
				return ret;
			}
			Register recv = ro.getRegister();

			assert recv.getType() instanceof jq_Class : "Receiver must be a ref type.";

			// generate pt-set for the receiver.
			StackObject so = getMemLocation(clz, caller, recv, ro.getType(), sum);
			AbstractHeap absHeap = sum.getAbsHeap();
			P2Set p2Set = absHeap.lookup(so,
					EpsilonFieldElem.getEpsilonFieldElem());

			// only one target, resolve it as static call.
			for (jq_Method tgt : tgtSet) {
				// generate constraint for each potential target.
				jq_Class tgtType = tgt.getDeclaringClass();
				String signature = tgt.toString();
				if (SummariesEnv.v().isStubMethod(signature))
					continue;

				if (SummariesEnv.v().disableCst() || sum.isInBadScc()) {
					cst = ConstraintManager.genTrue();
				} else
					cst = genCst(p2Set, tgt, tgtType, tgtSet);

				if (p2Set.isEmpty())
					cst = ConstraintManager.genTrue();

				assert cst != null : "Invalid constaint!";
				Summary dySum = SummariesEnv.v().getSummary(tgt);
				
				if (dySum == null)
					continue;

				if (dySum.hasAnalyzed())
					ret.add(new Pair<Summary, BoolExpr>(dySum, cst));
			}
		} 
		
		return ret;
	}
}
