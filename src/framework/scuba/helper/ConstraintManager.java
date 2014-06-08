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

import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.Env;
import framework.scuba.domain.HeapObject;

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
	public BoolExpr lift(HeapObject ho) {

		try {
			if (ho instanceof AllocElem) {
				// return the number of its class.
				AllocElem ae = (AllocElem) ho;
				jq_Type jType = ae.getAlloc().getType();
				assert jType instanceof jq_Class : "alloc object should be a jq_class.";
				Expr cur = ctx.MkInt(Env.getConstTerm4Class((jq_Class) jType));
				BoolExpr eq = ctx.MkEq(cur, ctx.MkInt("1"));
				//perform simplification like 2=2 -> true, 2=3 -> false
				Expr simEq = eq.Simplify();
				assert simEq instanceof BoolExpr : "Must return a boolean expr.";
				return (BoolExpr)simEq;
			} else if (ho instanceof AccessPath) {
				AccessPath ap = (AccessPath) ho;
				String symbol = "v" + ap.getId();
				Expr o = ctx.MkConst(symbol, ctx.IntSort());
				// type(o)
				IntExpr to = (IntExpr) typeFun.Apply(o);
				// type(o)=1
				BoolExpr eq = ctx.MkEq(to, ctx.MkInt("1"));
				// return int_constant vi where i is the id of ap.
				Expr simEq = eq.Simplify();
				assert simEq instanceof BoolExpr : "Must return a boolean expr.";
				return (BoolExpr)simEq;
			} else {
				assert false : "Unknown heap object.";
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
}
