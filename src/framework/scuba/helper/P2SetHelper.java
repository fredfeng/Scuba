package framework.scuba.helper;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.HeapObject;
import framework.scuba.domain.P2Set;

public class P2SetHelper {

	// the returned P2Set is a shallow copy with the same content of constraints
	// but different instances
	public static P2Set project(P2Set p2Set, BoolExpr otherCst) {

		P2Set ret = new P2Set();
		for (HeapObject obj : p2Set.getHeapObjects()) {
			// intersection is a shallow copy of the constraints with the same
			// content but different constraint instances
			BoolExpr newCst = ConstraintManager.intersect(
					p2Set.getConstraint(obj), otherCst);

			ret.put(obj, newCst);
		}
		assert (ret != null) : "we get a null P2Set!";
		return ret;
	}

	// the returned P2Set is a shallow copy with copied constraints
	public static P2Set join(P2Set pt1, P2Set pt2) {

		P2Set ret = new P2Set();
		for (HeapObject obj : pt1.getHeapObjects()) {
			if (pt2.containsHeapObject(obj)) {
				// newCst is a copy of constraints, which has nothing to do with
				// either constraints in the p2sets
				BoolExpr newCst = ConstraintManager.intersect(
						pt1.getConstraint(obj), pt2.getConstraint(obj));

				ret.put(obj, newCst);
			} else {
				// do clone() to get new constraints with the same content but
				// different instances
				ret.put(obj, ConstraintManager.clone(pt1.getConstraint(obj)));
			}
		}
		for (HeapObject obj : pt2.getHeapObjects()) {
			if (!pt1.containsHeapObject(obj)) {
				// do clone()
				ret.put(obj, ConstraintManager.clone(pt2.getConstraint(obj)));
			}
		}

		return ret;

	}
}
