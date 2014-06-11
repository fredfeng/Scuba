package framework.scuba.helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Type;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_lbool;

import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AbstractMemLoc;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.Env;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.InstantiatedLocSet;
import framework.scuba.domain.MemLocInstantiation;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.ProgramPoint;
import framework.scuba.utils.StringUtil;

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
	
	//map from term to heapObject. for unlifting.
	static Map<String, AccessPath> term2Ap = new HashMap<String, AccessPath>();
	
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
				//put to map for unlifting later.
				term2Ap.put(symbol, ap);
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
	
	//perform unlifting and instantiation. Rule 2 in figure 10.
	public static BoolExpr instConstaint(BoolExpr expr, AbstractHeap callerHeap,
			ProgramPoint point, MemLocInstantiation memLocInstn) {
		try {
			Set<BoolExpr> set = new HashSet<BoolExpr>();
			if(expr.IsAnd() || expr.IsOr())
				extractTerm(expr, set);
			else {
				assert expr.IsEq() || expr.IsLE() : "invalid expr" + expr;
				set.add(expr);
			}
			
			for (BoolExpr sub : set) {
				if (sub.IsEq() || sub.IsLE()) {
					Expr term = sub.Args()[0].Args()[0];
					//Using toString is ugly, but that's the only way i can get from Z3...
					AccessPath ap = term2Ap.get(term.toString());
					assert ap != null : "Fails to load access path for " + term
							+ " in " + term2Ap;
					assert sub.Args()[1] instanceof IntNum : "arg must be IntNum!";
					IntNum typeInt = (IntNum) sub.Args()[1];
					// get points-to set for term
					assert(ap instanceof AccessPath);
					InstantiatedLocSet p2Set = memLocInstn.instantiate(ap, callerHeap, point);
					BoolExpr instSub;
					if (sub.IsEq())
						instSub = instEqTyping(p2Set, typeInt.Int());
					else
						instSub = instSubTyping(p2Set, typeInt.Int());

					expr = (BoolExpr)expr.Substitute(sub, instSub);
				}
			}
			return expr;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	//given an expr, extract all its sub terms for instantiating.
	public static void extractTerm(Expr expr, Set<BoolExpr> set) {
        try {
			for(int i = 0; i < expr.NumArgs(); i++) {
				assert expr.Args()[i] instanceof BoolExpr : "Not BoolExpr:" + expr.Args()[i];
				BoolExpr sub = (BoolExpr)expr.Args()[i];
				if(sub.IsAnd() || sub.IsOr())
					extractTerm(sub, set);
				else {
					set.add(sub);
				}
			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	//A and B.
	public static BoolExpr intersect(BoolExpr first, BoolExpr second) {
        try {
        	assert first!= null : "Invalid constrait";
        	assert second!= null : "Invalid constrait";
			long startCst = System.nanoTime();

			BoolExpr inter = ctx.MkAnd(new BoolExpr[] { first, second });
			//try to simplify.
			long startSimplify = System.nanoTime();
//			Expr sim = inter.Simplify();
			if (G.tuning) {
				long endSimplify = System.nanoTime();
				G.cstSim += (endSimplify - startSimplify);
			}

			assert inter instanceof BoolExpr : "Unknown constraints in intersection!";
			return inter;
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
			long startCst = System.nanoTime();

			BoolExpr union = ctx.MkOr(new BoolExpr[] { first, second });
			//try to simplify.
			long startSimplify = System.nanoTime();
//			Expr sim = union.Simplify();
			if (G.tuning) {
				long endSimplify = System.nanoTime();
				G.cstSim += (endSimplify - startSimplify);
			}
			assert union instanceof BoolExpr : "Unknown constraints in union!";
			return union;
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
	
	// instantiate subtyping constraint by term.
	public static BoolExpr instSubTyping(InstantiatedLocSet p2Set, int typeInt) {
		long startICst = System.nanoTime();

		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for (AbstractMemLoc ho : p2Set.getAbstractMemLocs()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.getConstraint(ho);
				BoolExpr newCst = intersect(orgCst,
						lift((HeapObject) ho, typeInt, false));
				b = union(b, newCst);
			} else {
				assert false : "Invalid abstract MemLoc." + ho;
			}
		}
		if (G.tuning) {
			long endICst = System.nanoTime();
			G.instCstSubTime += (endICst - startICst);
		}
		return b;
	}

	// instantiate equality typing constraint by term.
	public static BoolExpr instEqTyping(InstantiatedLocSet p2Set, int typeInt) {
		long startICst = System.nanoTime();

		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for (AbstractMemLoc ho : p2Set.getAbstractMemLocs()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.getConstraint(ho);
				BoolExpr newCst = intersect(orgCst,
						lift((HeapObject) ho, typeInt, true));
				b = union(b, newCst);
			} else {
				assert false : "Invalid abstract MemLoc." + ho;
			}
		}
		if (G.tuning) {
			long endICst = System.nanoTime();
			G.instCstSubTime += (endICst - startICst);
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
	 * iff \neg(expr1 iff expr2) is unsatisfiable.
	 * @param expr1
	 * @param expr2
	 * @return
	 */
	public static boolean isEqual(BoolExpr expr1, BoolExpr expr2) {
		assert expr1 != null : "Constraint can not be null!";
		assert expr2 != null : "Constraint can not be null!";

		try {
			BoolExpr e1;
			e1 = ctx.MkIff(expr1, expr2);
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
