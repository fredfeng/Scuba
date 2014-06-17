package framework.scuba.domain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ConstraintManager;

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
		validate();
	}

	public void validate() {
		// validate that constraints in the main heap are all true
		for (Pair<AbstractMemLoc, FieldElem> pair : mainHeap.keySet()) {
			P2Set p2set = mainHeap.get(pair);
			for (HeapObject hObj : p2set.getHeapObjects()) {
				BoolExpr cst = p2set.getConstraint(hObj);
				assert (ConstraintManager.isTrue(cst)) : "constraint is not true: "
						+ cst;
				System.out.println("Constraint check PASSED!");
			}
		}
		// validate that constraints in the <clinit>'s heaps are all true
		for (AbstractHeap clinitHeap : clinitHeaps) {
			for (Pair<AbstractMemLoc, FieldElem> pair : clinitHeap.keySet()) {
				P2Set p2set = clinitHeap.get(pair);
				for (HeapObject hObj : p2set.getHeapObjects()) {
					BoolExpr cst = p2set.getConstraint(hObj);
					assert (ConstraintManager.isTrue(cst)) : "constraint is not true: "
							+ cst;
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

	public AbstractHeap sumAllHeaps() {
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
		// given an edge in the heap of a method, move it to the final heap
		boolean ret = false;

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				src, field);

		Pair<Boolean, Boolean> ret1 = sumHeap.weakUpdateNoNumbering(pair,
				new P2Set(tgt, ConstraintManager.genTrue()));

		ret = ret1.val0;
		return ret;
	}
}