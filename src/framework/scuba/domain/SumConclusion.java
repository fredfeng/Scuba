package framework.scuba.domain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.AccessPathHelper;
import framework.scuba.helper.CstManager;
import framework.scuba.helper.G;
import framework.scuba.utils.StringUtil;

public class SumConclusion {

	// the final heap that summarizes all top-level heaps
	final protected AbsHeap sumHeap;

	// the heaps of <clinit>'s methods
	final protected Set<AbsHeap> clinitHeaps;

	// the heap of the main method
	final protected AbsHeap mainHeap;

	final protected AbsHeapHandler ahh = new AbsHeapHandler();

	public SumConclusion(Set<AbsHeap> clinitHeaps, AbsHeap mainHeap) {
		this.sumHeap = new AbsHeap(null);
		this.clinitHeaps = clinitHeaps;
		this.mainHeap = mainHeap;
		this.ahh.setAbsHeap(sumHeap);
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
				local, Env.getEpsilonFieldElem()));
		if (p2set == null) {
			StringUtil.reportInfo("[dbgAntlr] "
					+ "we cannot get the p2set in the final heap.");
			return ret;
		}

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

		for (HeapObject hObj : p2set.keySet()) {
			if (hObj instanceof AllocElem) {
				ret.add((AllocElem) hObj);
			} else {
				if (SummariesEnv.v().resolveFinalHeap) {
					if (hObj instanceof StaticAccessPathElem) {
						assert (hObj instanceof StaticAccessPathElem)
								&& (((StaticAccessPathElem) hObj).getBase() instanceof StaticFieldElem) : ""
								+ "only StaticElem AccessPath allowed in the entry!";
						AccessPathHelper.resolve(sumHeap,
								(StaticAccessPathElem) hObj, ret);
					} else {
						System.out.println("[CANNOT FIND!]");
					}
				}
			}
		}
		return ret;
	}

	public AbsHeap sumAllHeaps() {
		// a worklist algorithm to conclude all heaps of <clinit>'s
		Set<AbsHeap> wl = new HashSet<AbsHeap>();
		Set<AbsHeap> analyzed = new HashSet<AbsHeap>();
		wl.addAll(clinitHeaps);

		if (G.tuning) {
			StringUtil
					.reportInfo("[Concluding] starting to conclude all heaps...");
		}
		if (SummariesEnv.v().topFixPoint) {
			while (true) {
				AbsHeap worker = wl.iterator().next();
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
			for (Iterator<AbsHeap> it = clinitHeaps.iterator(); it.hasNext();) {
				AbsHeap worker = it.next();
				instnHeap(worker);
			}
		}
		// then conclude the heap of the main method
		instnHeap(mainHeap);

		return sumHeap;
	}

	protected Pair<Boolean, Boolean> instnHeap(AbsHeap worker) {
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
			FieldElem field, AbsHeap worker) {
		// given an edge in the heap of a method, move it to the final heap
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(src,
				field);

		ret = ahh.weakUpdate(pair, new P2Set(tgt, CstManager.genTrue()));

		return ret;
	}
}
