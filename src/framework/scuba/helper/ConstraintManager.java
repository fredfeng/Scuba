package framework.scuba.helper;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Type;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;

import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.CstInstnCache;
import framework.scuba.domain.CstInstnCacheDepMap;
import framework.scuba.domain.Env;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.MemLocInstnItem;
import framework.scuba.domain.MemLocInstnSet;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.ProgramPoint;
import framework.scuba.domain.SummariesEnv;
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

	// define the uninterpreted function. type(o)=T
	static FuncDecl typeFun;

	// constant expr.
	static BoolExpr trueExpr;

	static BoolExpr falseExpr;

	// map from term to heapObject. for unlifting.
	static Map<String, AccessPath> term2Ap = new HashMap<String, AccessPath>();

	// this is my little cute cache for constraint instantiation
	public static final CstInstnCache instnCache = new CstInstnCache();

	static final Map<String, Map<String, BoolExpr>> extractCache = new HashMap<String, Map<String, BoolExpr>>();

	static final Map<String, BoolExpr> simplifyCache = new HashMap<String, BoolExpr>();

	static final Map<Pair<String, String>, BoolExpr> unionCache = new HashMap<Pair<String, String>, BoolExpr>();

	static final Map<Pair<String, String>, BoolExpr> interCache = new HashMap<Pair<String, String>, BoolExpr>();

	static final Map<Trio<String, String, String>, BoolExpr> subCache = new HashMap<Trio<String, String, String>, BoolExpr>();

	static final Map<Pair<String, String>, Boolean> eqCache = new HashMap<Pair<String, String>, Boolean>();

	// the dependence map for cst instantiation
	static final CstInstnCacheDepMap cstDepMap = new CstInstnCacheDepMap();

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

	// constraint generations.
	public static BoolExpr genTrue() {
		return trueExpr;
	}

	public static BoolExpr genFalse() {
		return falseExpr;
	}

	/**
	 * lift operation: Given a heap object, return its term. if ho is an
	 * allocElem, return its number; if ho is an accesspath, return its unique
	 * variable v_i where i is read from a global counter.
	 * 
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
				//always true for jq_array.
				if(jType instanceof jq_Array) 
					return (BoolExpr)cur;
				// assert jType instanceof jq_Class :
				// "alloc object should be a jq_class. "
				// + "[type]: " + jType + " [AllocElem]: " + ae;
				cur = ctx.MkInt(Env.getConstTerm4Class((jq_Class) jType));

			} else if (ho instanceof AccessPath) {
				AccessPath ap = (AccessPath) ho;
				String symbol = "v" + ap.getId();
				// put to map for unlifting later.
				term2Ap.put(symbol, ap);
				Expr o = ctx.MkConst(symbol, ctx.IntSort());
				// type(o)
				cur = typeFun.Apply(o);
			} else {
				assert false : "Unknown heap object.";
			}

			// lift type(o)=T
			if (isEqual) {
				BoolExpr eq = ctx.MkEq(cur, ctx.MkInt(typeInt));
				// perform simplification like 2=2 -> true, 2=3 -> false
				// Expr simEq = eq.Simplify();
				// assert simEq instanceof BoolExpr :
				// "Must return a boolean expr.";
				// return (BoolExpr)simEq;
				return eq;

				// lift type(o)<=T
			} else {
				BoolExpr le = ctx.MkLe((IntExpr) cur, ctx.MkInt(typeInt));
				// perform simplification like 2=2 -> true, 2=3 -> false
				// Expr simLe = le.Simplify();
				// assert simLe instanceof BoolExpr :
				// "Must return a boolean expr.";
				// return (BoolExpr)simLe;
				return le;
			}

		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	// perform unlifting and instantiation. Rule 2 in figure 10.
	public static BoolExpr instnConstaint(BoolExpr expr,
			AbstractHeap callerHeap, ProgramPoint point, MemLocInstnItem item) {

		BoolExpr ret = null;

		if (SummariesEnv.v().isUsingCstCache()) {
			ret = instnCache.getBoolExpr(item, expr);
			if (ret != null) {
				return ret;
			}
		}

		try {
			// generate a new instance!
			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: "
						+ "simplifying the constraints.");
			}

			if (SummariesEnv.v().isUsingSimplifyCache()) {
				ret = simplifyCache.get(expr.toString());
				if (ret == null) {
					ret = (BoolExpr) expr.Simplify();
					simplifyCache.put(expr.toString(), ret);
				}
			} else {
				ret = (BoolExpr) expr.Simplify();
			}

			if (isScala(ret))
				return ret;

			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: "
						+ "extracting the constraints." + "[size]"
						+ ret.toString().length() + " " + ret);
			}

			Map<String, BoolExpr> map;
			if (SummariesEnv.v().isUsingExtractCache()) {
				map = extractTermUsingCache(ret);
			} else {
				map = new HashMap<String, BoolExpr>();
				extractTerm(ret, map);
			}

			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: "
						+ "substituting constraints.");
			}

			BoolExpr ret1 = ret;
			for (BoolExpr sub : map.values()) {
				assert sub.IsEq() || sub.IsLE() : "invalid sub expr" + sub;
				Expr term = sub.Args()[0].Args()[0];
				// Using toString is ugly, but that's the only way i can get
				// from Z3...
				AccessPath ap = term2Ap.get(term.toString());
				assert ap != null : "Fails to load access path for " + term
						+ " in " + term2Ap;
				assert sub.Args()[1] instanceof IntNum : "arg must be IntNum!";
				IntNum typeInt = (IntNum) sub.Args()[1];
				// get points-to set for term
				assert (ap instanceof AccessPath);
				MemLocInstnSet p2Set = item.instnMemLoc(ap, callerHeap, point);

				// update the dependence relation
				if (SummariesEnv.v().isUsingCstCache()) {
					cstDepMap.add(ap, new Pair<MemLocInstnItem, BoolExpr>(item,
							expr));
				}

				BoolExpr instSub;
				if (sub.IsEq())
					instSub = instEqTyping(p2Set, typeInt.Int());
				else
					instSub = instSubTyping(p2Set, typeInt.Int());

				if (SummariesEnv.v().isUsingSubCache()) {
					BoolExpr tmp = subCache
							.get(new Trio<String, String, String>(ret1
									.toString(), sub.toString(), instSub
									.toString()));
					if (tmp == null) {
						tmp = (BoolExpr) ret1.Substitute(sub, instSub);
					}
					subCache.put(
							new Trio<String, String, String>(ret1.toString(),
									sub.toString(), instSub.toString()), tmp);
					ret1 = tmp;
				} else {
					ret1 = (BoolExpr) ret1.Substitute(sub, instSub);
				}
			}

			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: "
						+ "simplifying the constraint result.");
			}

			BoolExpr result = null;
			if (SummariesEnv.v().isUsingSimplifyCache()) {
				result = simplifyCache.get(ret1.toString());
				if (result == null) {
					result = (BoolExpr) ret1.Simplify();
					simplifyCache.put(ret1.toString(), result);
				}
			} else {
				result = (BoolExpr) ret1.Simplify();
			}

			if (SummariesEnv.v().isUsingCstCache()) {
				instnCache
						.add(item, new Pair<BoolExpr, BoolExpr>(expr, result));
			}

			return result;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static Map<String, BoolExpr> extractTermUsingCache(Expr expr)
			throws Z3Exception {
		Map<String, BoolExpr> ret = extractCache.get(expr.toString());
		if (ret != null) {
			return ret;
		}
		ret = new HashMap<String, BoolExpr>();

		// using toString as the key to map the same expr, buggy.
		if (G.instnInfo) {
			StringUtil.reportInfo("instnInfo: "
					+ "extracting the terms in constraint: " + expr);
		}
		if (expr.IsEq() || expr.IsLE()) {
			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: " + "base case: " + expr);
			}
			ret.put(expr.toString(), (BoolExpr) expr);
		} else if (expr.IsAnd() || expr.IsOr()) {
			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: " + "[recursive case:] "
						+ expr);
			}
			for (int i = 0; i < expr.NumArgs(); i++) {
				assert expr.Args()[i] instanceof BoolExpr : "Not BoolExpr:"
						+ expr.Args()[i];
				BoolExpr sub = (BoolExpr) expr.Args()[i];
				Map<String, BoolExpr> map = extractTermUsingCache(sub);
				ret.putAll(map);
			}
		}

		extractCache.put(expr.toString(), ret);
		return ret;
	}

	// given an expr, extract all its sub terms for instantiating.
	public static void extractTerm(Expr expr, Map<String, BoolExpr> map) {
		try {
			// using toString as the key to map the same expr, buggy.
			if (G.instnInfo) {
				StringUtil.reportInfo("instnInfo: "
						+ "extracting the terms in constraint: " + expr);
			}
			if (expr.IsEq() || expr.IsLE()) {
				if (G.instnInfo) {
					StringUtil.reportInfo("instnInfo: " + "base case: " + expr);
				}
				map.put(expr.toString(), (BoolExpr) expr);
				return;
			}

			if (expr.IsAnd() || expr.IsOr()) {
				if (G.instnInfo) {
					StringUtil.reportInfo("instnInfo: " + "[recursive case:] "
							+ expr);
				}
				for (int i = 0; i < expr.NumArgs(); i++) {
					assert expr.Args()[i] instanceof BoolExpr : "Not BoolExpr:"
							+ expr.Args()[i];
					BoolExpr sub = (BoolExpr) expr.Args()[i];
					extractTerm(sub, map);
				}
			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// A and B.
	public static BoolExpr intersect(BoolExpr first, BoolExpr second) {

		BoolExpr ret = null;
		if (SummariesEnv.v().isUsingInterCache()) {
			ret = interCache.get(new Pair<String, String>(first.toString(),
					second.toString()));
			if (ret != null) {
				return ret;
			}
		}

		if (SummariesEnv.v().disableCst())
			return trueExpr;
		try {
			assert first != null : "Invalid constrait";
			assert second != null : "Invalid constrait";
			if (isFalse(first) || isFalse(second)) {
				ret = falseExpr;
			} else if (isTrue(first)) {
				ret = second;
			} else if (isTrue(second)) {
				ret = first;
			} else {
				ret = ctx.MkAnd(new BoolExpr[] { first, second });
			}

			if (SummariesEnv.v().isUsingInterCache()) {
				interCache.put(new Pair<String, String>(first.toString(),
						second.toString()), ret);
			}
			// try to simplify.
			// Expr sim = inter.Simplify();
			// assert inter instanceof BoolExpr :
			// "Unknown constraints in intersection!";
			return ret;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	// A or B
	public static BoolExpr union(BoolExpr first, BoolExpr second) {
		BoolExpr ret = null;
		if (SummariesEnv.v().isUsingUnionCache()) {
			ret = unionCache.get(new Pair<String, String>(first.toString(),
					second.toString()));
			if (ret != null) {
				return ret;
			}
		}

		if (SummariesEnv.v().disableCst())
			return trueExpr;

		try {
			assert first != null : "Invalid constrait";
			assert second != null : "Invalid constrait";

			if (isTrue(first) || isTrue(second)) {
				ret = trueExpr;
			} else if (isFalse(first)) {
				ret = second;
			} else if (isFalse(second)) {
				ret = first;
			} else {
				ret = ctx.MkOr(new BoolExpr[] { first, second });
			}

			if (SummariesEnv.v().isUsingUnionCache()) {
				unionCache.put(new Pair<String, String>(first.toString(),
						second.toString()), ret);
			}
			// try to simplify.
			// Expr sim = union.Simplify();
			// assert sim instanceof BoolExpr : "Unknown constraints in union!";
			return ret;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	// generate subtyping constraint.
	public static BoolExpr genSubTyping(P2Set p2Set, jq_Class t) {
		int typeInt = Env.getConstTerm4Class(t);
		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for (HeapObject ho : p2Set.keySet()) {
			BoolExpr orgCst = p2Set.get(ho);
			BoolExpr newCst = intersect(orgCst, lift(ho, typeInt, false));
			if (isTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	// generate equality typing constraint.
	public static BoolExpr genEqTyping(P2Set p2Set, jq_Class t) {

		int typeInt = Env.getConstTerm4Class(t);
		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for (HeapObject ho : p2Set.keySet()) {
			BoolExpr orgCst = p2Set.get(ho);
			BoolExpr newCst = intersect(orgCst, lift(ho, typeInt, true));
			if (isTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	// instantiate subtyping constraint by term.
	public static BoolExpr instSubTyping(MemLocInstnSet p2Set, int typeInt) {
		long startICst = System.nanoTime();

		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for (AbsMemLoc ho : p2Set.keySet()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.get(ho);
				BoolExpr newCst = intersect(orgCst,
						lift((HeapObject) ho, typeInt, false));
				if (isTrue(newCst))
					return trueExpr;
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
	public static BoolExpr instEqTyping(MemLocInstnSet p2Set, int typeInt) {
		long startICst = System.nanoTime();

		BoolExpr b = genFalse();
		assert typeInt > 0 : "Invalid type int.";
		for (AbsMemLoc ho : p2Set.keySet()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.get(ho);
				BoolExpr newCst = intersect(orgCst,
						lift((HeapObject) ho, typeInt, true));
				if (isTrue(newCst))
					return trueExpr;

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
	 * Z3 doesn't provide a clone function. Use A' = A or A to generate a new
	 * one
	 * 
	 * @param expr
	 * @return
	 */
	public static BoolExpr clone(BoolExpr expr) {
		assert expr != null : "Unknown boolExpr.";
		// actually we don't need to clone a new instance. Since we will create
		// a fresh new one during intersect or union
		return expr;
		/*
		 * try { BoolExpr clone = ctx.MkOr(new BoolExpr[] { expr, expr });
		 * return (BoolExpr)clone.Simplify(); } catch (Z3Exception e) { // TODO
		 * Auto-generated catch block e.printStackTrace(); } return null;
		 */
	}

	/**
	 * Check whether expr1 and expr2 are equivalent. iff \neg(expr1 iff expr2)
	 * is unsatisfiable.
	 * 
	 * @param expr1
	 * @param expr2
	 * @return
	 */
	public static boolean isEqual(BoolExpr expr1, BoolExpr expr2) {

		boolean ret = false;

		assert expr1 != null : "Constraint can not be null!";
		assert expr2 != null : "Constraint can not be null!";

		if (SummariesEnv.v().isUsingEqCache()) {
			Pair<String, String> pair = new Pair<String, String>(
					expr1.toString(), expr2.toString());
			if (eqCache.containsKey(pair)) {
				return eqCache.get(pair);
			}
		}

		try {
            solver.Reset();
			BoolExpr e1;
			e1 = ctx.MkIff(expr1, expr2);
			BoolExpr e2 = ctx.MkNot(e1);
			solver.Assert(e2);
			if (solver.Check() == Status.UNSATISFIABLE) {
				ret = true;
				if (SummariesEnv.v().isUsingEqCache()) {
					eqCache.put(new Pair<String, String>(expr1.toString(),
							expr2.toString()), ret);
				}
				return ret;
			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ret = false;
		if (SummariesEnv.v().isUsingEqCache()) {
			eqCache.put(
					new Pair<String, String>(expr1.toString(), expr2.toString()),
					ret);
		}

		return ret;
	}

	// check if cst is a scala constraint, e.g, true, false
	public static boolean isScala(BoolExpr cst) {
		try {
			if (cst.IsTrue() || cst.IsFalse())
				return true;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public static boolean isTrue(BoolExpr cst) {
		try {
			return cst.IsTrue();
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public static boolean isFalse(BoolExpr cst) {
		try {
			return cst.IsFalse();
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public static CstInstnCacheDepMap getCstDepMap() {
		return ConstraintManager.cstDepMap;
	}

	public static CstInstnCache getCstInstnCache() {
		return ConstraintManager.instnCache;
	}
}
