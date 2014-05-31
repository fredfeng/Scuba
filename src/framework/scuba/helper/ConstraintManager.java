package framework.scuba.helper;

import joeq.Class.jq_Type;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.Constraint;
import framework.scuba.domain.FalseConstraint;
import framework.scuba.domain.TrueConstraint;
import framework.scuba.domain.TypeEqConstraint;

/**
 * Class for generating/solving constraints, since right now our system can only
 * have true | false | Type(v) = T. We could always keep the length of
 * constraints to 1. For generality, we should use a constraint solver in the
 * future.
 * 
 * @author yufeng
 * 
 */
public class ConstraintManager {

	//constraint generations.
	public static Constraint genTrue() {
		return new TrueConstraint();
	}

	public static Constraint genFalse() {
		return new FalseConstraint();
	}
	
	public static Constraint genTypeEq(AccessPath ap, jq_Type t) {
		return new TypeEqConstraint(ap, t);
	}

	//constraints solving.
	public static Constraint intersect(Constraint first, Constraint second) {
		if ((first instanceof FalseConstraint)
				|| (second instanceof FalseConstraint))
			return new FalseConstraint();
		
		if (first instanceof TrueConstraint)
			return second;
		
		if (second instanceof TrueConstraint)
			return first;

		assert false : "Unknown constraints in intersection!";
		return null;
	}

	public static Constraint union(Constraint first, Constraint second) {
		if ((first instanceof TrueConstraint)
				|| (second instanceof TrueConstraint))
			return new TrueConstraint();
		
		if (first instanceof FalseConstraint)
			return second;
		
		if (second instanceof FalseConstraint)
			return first;

		assert false : "Unknown constraints in union!";
		return null;
	}
}
