package framework.scuba.helper;

import joeq.Class.jq_Class;
import joeq.Class.jq_Type;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_lbool;

import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.Env;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.SummariesEnv;

/**
 * Class for generating/solving constraints, since right now our system can only
 * have true | false | Type(v) = T. 
 * 
 * Integrate Z3 to perform the constraint solving and simplification.
 * 
 * @author yufeng
 * 
 */
public class ConstraintManager {
	
	private static ConstraintManager instance = new ConstraintManager();

	static Context ctx;
	
    static Solver solver;
    
	//define the uninterpreted function. type(o)=T
    static FuncDecl typeFun;

	//constant expr.
	static BoolExpr trueExpr;
	
	static BoolExpr falseExpr;
	
	public ConstraintManager() {
		try {
			ctx = new Context();
		    solver = ctx.MkSolver();
			trueExpr = ctx.MkBool(true);
			falseExpr = ctx.MkBool(false);
			typeFun = ctx.MkFuncDecl("type", ctx.IntSort(), ctx.IntSort());
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public static ConstraintManager v() {
		return instance;
	}
	
	//constraint generations.
	public static BoolExpr genTrue() {
		return trueExpr;
	}

	public static BoolExpr genFalse() {
		return falseExpr;
	}
	
	/**
	 * lift operation: Given a heap object, return its term.
	 * if ho is an allocElem, return its number;
	 * if ho is an accesspath, return its unique variable v_i
	 * where i is read from a global counter.
	 * @param ho
	 * @return
	 */
	public static BoolExpr lift(HeapObject ho, int typeInt, boolean isEqual) {
		Expr cur = genTrue();
		try {
			if (ho instanceof AllocElem) {
				// return the number of its class.
				AllocElem ae = (AllocElem) ho;
				jq_Type jType = ae.getAlloc().getType();
				assert jType instanceof jq_Class : "alloc object should be a jq_class.";
				cur = ctx.MkInt(Env.getConstTerm4Class((jq_Class) jType));

			} else if (ho instanceof AccessPath) {
				AccessPath ap = (AccessPath) ho;
				String symbol = "v" + ap.getId();
				Expr o = ctx.MkConst(symbol, ctx.IntSort());
				// type(o)
				cur = typeFun.Apply(o);
			} else {
				assert false : "Unknown heap object.";
			}
			
			//lift type(o)=T
			if(isEqual) {
				BoolExpr eq = ctx.MkEq(cur, ctx.MkInt(typeInt));
				//perform simplification like 2=2 -> true, 2=3 -> false
				Expr simEq = eq.Simplify();
				assert simEq instanceof BoolExpr : "Must return a boolean expr.";
				return (BoolExpr)simEq;

			//lift type(o)<=T
			} else {
				BoolExpr le = ctx.MkLe((IntExpr)cur, ctx.MkInt(typeInt));
				//perform simplification like 2=2 -> true, 2=3 -> false
				Expr simLe = le.Simplify();
				assert simLe instanceof BoolExpr : "Must return a boolean expr.";
				return (BoolExpr)simLe;
			}

		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	//A and B.
	public static BoolExpr intersect(BoolExpr first, BoolExpr second) {
        try {
        	assert first!= null : "Invalid constrait";
        	assert second!= null : "Invalid constrait";

			BoolExpr inter = ctx.MkAnd(new BoolExpr[] { first, second });
			//try to simplify.
			Expr sim = inter.Simplify();
			assert sim instanceof BoolExpr : "Unknown constraints in intersection!";
			return (BoolExpr)sim;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	//A or B
	public static BoolExpr union(BoolExpr first, BoolExpr second) {
        try {
        	assert first!= null : "Invalid constrait";
        	assert second!= null : "Invalid constrait";
        	
			BoolExpr union = ctx.MkOr(new BoolExpr[] { first, second });
			//try to simplify.
			Expr sim = union.Simplify();
			assert sim instanceof BoolExpr : "Unknown constraints in union!";
			return (BoolExpr)sim;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	//generate subtyping constraint.
	public static BoolExpr genSubTyping(P2Set p2Set, jq_Class t) {
		int typeInt = Env.getConstTerm4Class(t);
		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for(HeapObject ho : p2Set.getHeapObjects()) {
			BoolExpr orgCst = p2Set.getConstraint(ho);
			BoolExpr newCst = intersect(orgCst, lift(ho, typeInt, false));
			b = union(b, newCst);
		}
		return b;
	}
	
	//generate equality typing constraint.
	public static BoolExpr genEqTyping(P2Set p2Set, jq_Class t) {
		int typeInt = Env.getConstTerm4Class(t);
		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for(HeapObject ho : p2Set.getHeapObjects()) {
			BoolExpr orgCst = p2Set.getConstraint(ho);
			BoolExpr newCst = intersect(orgCst, lift(ho, typeInt, true));
			b = union(b, newCst);
		}
		return b;
	}
	
	/**
	 * Z3 doesn't provide a clone function. Use A' = A or A to generate a new one 
	 * @param expr
	 * @return
	 */
	public static BoolExpr clone(BoolExpr expr) {
		assert expr != null : "Unknown boolExpr.";
        try {
			BoolExpr clone = ctx.MkOr(new BoolExpr[] { expr, expr });
			return (BoolExpr)clone.Simplify();
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Check whether expr1 and expr2 are equivalent.
	 * @param expr1
	 * @param expr2
	 * @return
	 */
	public static boolean isEqual(BoolExpr expr1, BoolExpr expr2) {
		assert expr1 != null : "Constraint can not be null!";
		assert expr2 != null : "Constraint can not be null!";

		try {
			BoolExpr e1;
			e1 = ctx.MkEq(expr1, expr2);
			BoolExpr e2 = ctx.MkNot(e1);
			solver.Assert(e2);
			if(solver.Check() == Status.UNSATISFIABLE)
				return true;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	//check if cst is a scala constraint, e.g, true, false 
	public static boolean isScala(BoolExpr cst) {
		try {
			if ((cst.BoolValue() == Z3_lbool.Z3_L_FALSE)
					|| (cst.BoolValue() == Z3_lbool.Z3_L_TRUE))
				return true;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
}
