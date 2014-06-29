package framework.scuba.domain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;

import framework.scuba.helper.AccessPathHelper;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.G;
import framework.scuba.utils.StringUtil;

public class SumConclusion {

	// the final heap that summarizes all top-level heaps
	final protected AbstractHeap sumHeap;

	// the heaps of <clinit>'s methods
	final protected Set<AbstractHeap> clinitHeaps;

	// the heap of the main method
	final protected AbstractHeap mainHeap;

	public SumConclusion(Set<AbstractHeap> clinitHeaps, AbstractHeap mainHeap) {
		this.sumHeap = new AbstractHeap(null);
		this.clinitHeaps = clinitHeaps;
		this.mainHeap = mainHeap;
	}

	public void validate() {
		// validate that constraints in the main heap are all true
		for (Pair<AbsMemLoc, FieldElem> pair : mainHeap.keySet()) {
			P2Set p2set = mainHeap.get(pair);
			for (HeapObject hObj : p2set.keySet()) {
				BoolExpr cst = p2set.get(hObj);
				try {
					assert (ConstraintManager.isTrue((BoolExpr) cst.Simplify())) : ""
							+ "constraint is not true: " + cst;
				} catch (Z3Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("Constraint check PASSED!");
			}
		}
		// validate that constraints in the <clinit>'s heaps are all true
		for (AbstractHeap clinitHeap : clinitHeaps) {
			for (Pair<AbsMemLoc, FieldElem> pair : clinitHeap.keySet()) {
				P2Set p2set = clinitHeap.get(pair);
				for (HeapObject hObj : p2set.keySet()) {
					BoolExpr cst = p2set.get(hObj);
					// validate the constraints to be true
					try {
						assert (ConstraintManager.isTrue((BoolExpr) cst
								.Simplify())) : "constraint is not true: "
								+ cst;
					} catch (Z3Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("Constraint check PASSED!");
				}
			}
		}
	}

	public int getHeapSize() {
		return sumHeap.size();
	}

	public int size() {
		return clinitHeaps.size() + 1;
	}

	public Set<AllocElem> getP2Set(LocalVarElem local, EpsilonFieldElem e) {
		Set<AllocElem> ret = new HashSet<AllocElem>();
		assert (local != null);
		P2Set p2set = sumHeap.locToP2Set.get(new Pair<AbsMemLoc, FieldElem>(
				local, EpsilonFieldElem.getEpsilonFieldElem()));
		if (p2set == null) {
			return ret;
		}

		if (G.dbgAntlr) {
			StringUtil.reportInfo("[dbgAntlr] " + "[p2 set in final summary] ");
			StringUtil
					.reportInfo("[dbgAntlr] "
							+ "------------------------------------------------------------------------------------");
			for (HeapObject hObj : p2set.keySet()) {
				StringUtil.reportInfo("[dbgAntlr] " + hObj);
			}
			StringUtil
					.reportInfo("[dbgAntlr] "
							+ "------------------------------------------------------------------------------------");
		}

		for (HeapObject hObj : p2set.keySet()) {
			if (hObj instanceof AllocElem) {
				ret.add((AllocElem) hObj);
			} else {
				// if (hObj instanceof StaticAccessPath) {
				// assert (hObj instanceof StaticAccessPath)
				// && (hObj.findRoot() instanceof StaticElem) : ""
				// + "only StaticElem AccessPath allowed in the entry!";
				// AccessPathHelper.resolve(sumHeap, (StaticAccessPath) hObj,
				// ret);
				// } else {
				// System.out.println("[CANNOT FIND!]");
				// }
			}
		}
		return ret;
	}

	public AbstractHeap sumAllHeaps() {
		// a worklist algorithm to conclude all heaps of <clinit>'s
		Set<AbstractHeap> wl = new HashSet<AbstractHeap>();
		Set<AbstractHeap> analyzed = new HashSet<AbstractHeap>();
		wl.addAll(clinitHeaps);

		if (G.tuning) {
			StringUtil
					.reportInfo("[Concluding] starting to conclude all heaps...");
		}
		if (SummariesEnv.v().topFixPoint) {
			while (true) {
				AbstractHeap worker = wl.iterator().next();
				wl.remove(worker);
				if (analyzed.contains(worker)) {
					continue;
				}

				Pair<Boolean, Boolean> changed = instnHeap(worker);
				// TODO
				// currently we do this again only when the summary is changed
				if (changed.val1) {
					analyzed.clear();
				} else {
					analyzed.add(worker);
				}

				if (analyzed.size() == clinitHeaps.size()) {
					break;
				}

				wl.addAll(clinitHeaps);
			}
		} else {
			for (Iterator<AbstractHeap> it = clinitHeaps.iterator(); it
					.hasNext();) {
				AbstractHeap worker = it.next();
				instnHeap(worker);
			}
		}
		// then conclude the heap of the main method
		instnHeap(mainHeap);

		return sumHeap;
	}

	protected Pair<Boolean, Boolean> instnHeap(AbstractHeap worker) {
		// a fix-point algorithm to conclude the heap of a method
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		while (true) {
			boolean go = false;

			Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = worker.locToP2Set
					.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it.next();
				Pair<AbsMemLoc, FieldElem> key = entry.getKey();
				P2Set tgts = entry.getValue();
				AbsMemLoc src = key.val0;
				FieldElem f = key.val1;

				Iterator<Map.Entry<HeapObject, BoolExpr>> it1 = tgts.iterator();
				while (it1.hasNext()) {
					Map.Entry<HeapObject, BoolExpr> entry1 = it1.next();
					HeapObject tgt = entry1.getKey();
					Pair<Boolean, Boolean> res = instnEdge(src, tgt, f, worker);
					ret.val0 = res.val0 | ret.val0;
					ret.val1 = res.val1 | ret.val1;
					// if changing the heap then go (conservative)
					go = res.val0 | go;
				}
			}

			if (!go) {
				break;
			}

			if (!SummariesEnv.v().useFixPoint) {
				break;
			}
		}

		return ret;
	}

	protected Pair<Boolean, Boolean> instnEdge(AbsMemLoc src, HeapObject tgt,
			FieldElem field, AbstractHeap worker) {
		// given an edge in the heap of a method, move it to the final heap
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(src,
				field);

		ret = sumHeap.weakUpdate(pair,
				new P2Set(tgt, ConstraintManager.genTrue()));

		return ret;
	}
}
