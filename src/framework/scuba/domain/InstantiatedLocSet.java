package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ConstraintManager;

public class InstantiatedLocSet {

	protected Map<AbstractMemLoc, BoolExpr> instnLocSet = new HashMap<AbstractMemLoc, BoolExpr>();

	public InstantiatedLocSet() {

	}

	public InstantiatedLocSet(AbstractMemLoc loc, BoolExpr constraint) {
		instnLocSet.put(loc, constraint);
	}

	public void put(AbstractMemLoc loc, BoolExpr constraint) {
		instnLocSet.put(loc, constraint);
	}

	public boolean containsAbstractMemLoc(AbstractMemLoc loc) {
		return instnLocSet.containsKey(loc);
	}

	public Set<AbstractMemLoc> getAbstractMemLocs() {
		return instnLocSet.keySet();
	}

	public BoolExpr getConstraint(AbstractMemLoc loc) {
		return instnLocSet.get(loc);
	}

	public Map<AbstractMemLoc, BoolExpr> getInstnLocSet() {
		return instnLocSet;
	}

	public boolean isEmpty() {
		return instnLocSet.isEmpty();
	}

	public int size() {
		return instnLocSet.size();
	}

	public boolean join(P2Set other) {
		boolean ret = false;
		for (HeapObject hObj : other.getHeapObjects()) {
			if (instnLocSet.containsKey(hObj)) {
				// loc is in both sets
				// directly get the other set's constraints
				BoolExpr otherCst = other.getConstraint(hObj);

				// check whether we need to union the constraint
				// TODO maybe we can comment the following
				if (instnLocSet.get(hObj).equals(otherCst))
					continue;

				// generate the union of the two (a shallow copy with the same
				// constraints but different instances)
				BoolExpr newCst = ConstraintManager.union(
						instnLocSet.get(hObj), otherCst);

				instnLocSet.put(hObj, newCst);
				ret = true;
			} else {
				// loc is only in other's set
				// AVOID directly get the constraint instance of the other
				// set!!!! only get the shallow copy of the other constraints

				// for this case, we should add a new edge
				instnLocSet.put(hObj,
						ConstraintManager.clone(other.getConstraint(hObj)));
				ret = true;
			}
		}
		return ret;
	}
}
