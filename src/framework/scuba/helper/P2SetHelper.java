package framework.scuba.helper;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.HeapObject;
import framework.scuba.domain.P2Set;

public class P2SetHelper {

	// the returned P2Set is a shallow copy with the same content of constraints
	// but different instances
	public static P2Set project(P2Set p2Set, BoolExpr otherCst) {

		P2Set ret = new P2Set();
		for (HeapObject obj : p2Set.keySet()) {
			// intersection is a shallow copy of the constraints with the same
			// content but different constraint instances
			BoolExpr newCst = CstManager.intersect(
					p2Set.get(obj), otherCst);

			ret.put(obj, newCst);
		}
		assert (ret != null) : "we get a null P2Set!";
		return ret;
	}

	// the returned P2Set is a shallow copy with copied constraints
	public static P2Set join(P2Set pt1, P2Set pt2) {

		P2Set ret = new P2Set();
		for (HeapObject obj : pt1.keySet()) {
			if (pt2.contains(obj)) {
				// newCst is a copy of constraints, which has nothing to do with
				// either constraints in the p2sets
				BoolExpr newCst = CstManager.intersect(
						pt1.get(obj), pt2.get(obj));

				ret.put(obj, newCst);
			} else {
				// do clone() to get new constraints with the same content but
				// different instances
				ret.put(obj, CstManager.clone(pt1.get(obj)));
			}
		}
		for (HeapObject obj : pt2.keySet()) {
			if (!pt1.contains(obj)) {
				// do clone()
				ret.put(obj, CstManager.clone(pt2.get(obj)));
			}
		}

		return ret;

	}
}
