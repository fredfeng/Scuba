package framework.scuba.domain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ConstraintManager;

public class SumConclusion {

	final protected AbstractHeap sumHeap;

	public SumConclusion() {
		this.sumHeap = new AbstractHeap(null);
	}

	public int getHeapSize() {
		return sumHeap.size();
	}

	public Set<AllocElem> getP2Set(LocalVarElem local) {
		Set<AllocElem> ret = new HashSet<AllocElem>();
		assert (local != null);
		P2Set p2set = sumHeap.heapObjectsToP2Set
				.get(new Pair<AbstractMemLoc, FieldElem>(local,
						EpsilonFieldElem.getEpsilonFieldElem()));
		if (p2set == null) {
			return ret;
		}
		for (HeapObject hObj : p2set.getHeapObjects()) {
			if (hObj instanceof AllocElem) {
				ret.add((AllocElem) hObj);
			} else {
				assert (hObj instanceof AccessPath)
						&& (hObj.findRoot() instanceof StaticElem) : ""
						+ "only StaticElem AccessPath allowed in the entry!";
				sumHeap.resolve((AccessPath) hObj, ret);
			}
		}
		return ret;
	}

	public AbstractHeap sumAllHeaps(Set<AbstractHeap> clinitHeaps,
			AbstractHeap mainHeap) {
		// a worklist algorithm to conclude all heaps of <clinit>'s
		Set<AbstractHeap> wl = new HashSet<AbstractHeap>();
		Set<AbstractHeap> analyzed = new HashSet<AbstractHeap>();
		wl.addAll(clinitHeaps);

		while (true) {
			AbstractHeap worker = wl.iterator().next();
			wl.remove(worker);
			if (analyzed.contains(worker)) {
				continue;
			}

			boolean changed = instnHeap(worker);
			if (changed) {
				analyzed.clear();
			} else {
				analyzed.add(worker);
			}

			if (analyzed.size() == clinitHeaps.size()) {
				break;
			}

			wl.addAll(clinitHeaps);
		}
		// then conclude the heap of the main method
		instnHeap(mainHeap);

		return sumHeap;
	}

	protected boolean instnHeap(AbstractHeap worker) {
		// a fix-point algorithm to conclude the heap of a method
		boolean ret = false;
		while (true) {
			boolean go = false;

			Iterator<Map.Entry<Pair<AbstractMemLoc, FieldElem>, P2Set>> it = worker.heapObjectsToP2Set
					.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Pair<AbstractMemLoc, FieldElem>, P2Set> entry = it
						.next();
				Pair<AbstractMemLoc, FieldElem> key = entry.getKey();
				P2Set tgts = entry.getValue();
				AbstractMemLoc src = key.val0;
				FieldElem f = key.val1;

				Iterator<Map.Entry<HeapObject, BoolExpr>> it1 = tgts.iterator();
				while (it1.hasNext()) {
					Map.Entry<HeapObject, BoolExpr> entry1 = it1.next();
					HeapObject tgt = entry1.getKey();
					go = instnEdge(src, tgt, f, worker) | go;
					ret = ret | go;
				}
			}

			if (!go) {
				break;
			}
		}

		return ret;
	}

	protected boolean instnEdge(AbstractMemLoc src, HeapObject tgt,
			FieldElem field, AbstractHeap worker) {
		boolean ret = false;
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				src, field);

		Pair<Boolean, Boolean> ret1 = sumHeap.weakUpdateNoNumbering(pair,
				new P2Set(tgt, ConstraintManager.genTrue()));

		ret = ret1.val0;
		return ret;
	}
}
