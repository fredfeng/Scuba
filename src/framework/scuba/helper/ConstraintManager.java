package framework.scuba.helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import com.microsoft.z3.Params;
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

	// define the uninterpreted function. type(o)=T
	static FuncDecl typeFun;
	
	static Solver solver;

	// constant expr.
	static BoolExpr trueExpr;

	static BoolExpr falseExpr;

	// map from term to heapObject. for unlifting.
	static Map<String, AccessPath> term2Ap = new HashMap<String, AccessPath>();

	// map from smashed accesspath to a counter
	static Map<AccessPath, Integer> ap2Counter = new HashMap<AccessPath, Integer>();

	// this is my little cute cache for constraint instantiation
	public static final CstInstnCache instnCache = new CstInstnCache();

	static final Map<BoolExpr, Map<BoolExpr, BoolExpr>> extractCache = new HashMap<BoolExpr, Map<BoolExpr, BoolExpr>>();

	static final Map<BoolExpr, BoolExpr> simplifyCache = new HashMap<BoolExpr, BoolExpr>();

	static final Map<Pair<BoolExpr, BoolExpr>, BoolExpr> unionCache = new HashMap<Pair<BoolExpr, BoolExpr>, BoolExpr>();

	static final Map<Pair<BoolExpr, BoolExpr>, BoolExpr> interCache = new HashMap<Pair<BoolExpr, BoolExpr>, BoolExpr>();
	
	static final Map<Pair<MemLocInstnSet, jq_Class>, BoolExpr> myInstCache = new HashMap<Pair<MemLocInstnSet, jq_Class>, BoolExpr>();

	static final Map<Trio<BoolExpr, BoolExpr, BoolExpr>, BoolExpr> subCache = new HashMap<Trio<BoolExpr, BoolExpr, BoolExpr>, BoolExpr>();

	static final Map<Pair<BoolExpr, BoolExpr>, Boolean> eqCache = new HashMap<Pair<BoolExpr, BoolExpr>, Boolean>();

	// the dependence map for cst instantiation
	static final CstInstnCacheDepMap cstDepMap = new CstInstnCacheDepMap();

	public ConstraintManager() {
		try {
			ctx = new Context();
		    solver = ctx.mkSolver();
	        Params solver_params = ctx.mkParams();
	        solver_params.add("ignore_solver1", true);
	        solver.setParameters(solver_params);
		    
			trueExpr = ctx.mkBool(true);
			falseExpr = ctx.mkBool(false);
			typeFun = ctx.mkFuncDecl("type", ctx.getIntSort(), ctx.getIntSort());
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

	public static BoolExpr instnConstaintNew(BoolExpr expr,
			AbstractHeap callerHeap, ProgramPoint point, MemLocInstnItem item) {
		BoolExpr ret = null;
		// check hitting cache
		if (SummariesEnv.v().isUsingCstCache()) {
			ret = instnCache.getBoolExpr(item, expr);
			if (ret != null) {
				return ret;
			}
		}

		try {
			if (SummariesEnv.v().isUsingSimplifyCache()) {
				ret = simplifyCache.get(expr);
				if (ret == null) {
					ret = (BoolExpr) expr.simplify();
					simplifyCache.put(expr, ret);
				}
			} else {
				ret = (BoolExpr) expr.simplify();
			}
			// ret = (BoolExpr) expr.Simplify();

			if (isScala(ret))
				return ret;

			long inststart = System.nanoTime();

			Map<BoolExpr, BoolExpr> map;
			map = new HashMap<BoolExpr, BoolExpr>();
			if (SummariesEnv.v().isUsingExtractCache()) {
				long unstart = System.nanoTime();
				map = extractTermUsingCache(ret);
				long unend = System.nanoTime();
				G.extractTime += (unend - unstart);
			} else {
				extractTerm(ret, map);
			}
			// extractTerm(ret, map);

			BoolExpr ret1 = ret;
			for (BoolExpr sub : map.values()) {
				assert sub.isEq() || sub.isLE() || sub.isGE() : "invalid sub expr"
						+ sub;
				Expr term = sub.getArgs()[0].getArgs()[0];
				String termStr = term.toString();
				// Using toString is ugly, but that's the only way i can get
				// from Z3...
				AccessPath ap = (AccessPath) liftInv(termStr);
				assert ap != null;

				assert sub.getArgs()[1] instanceof IntNum : "arg must be IntNum!";
				IntNum typeInt = (IntNum) sub.getArgs()[1];
				jq_Class t = Env.class2TermRev.get(typeInt.getInt());
				assert t != null : typeInt;
				// get points-to set for term
				MemLocInstnSet p2Set = item.instnMemLoc(ap, callerHeap, point);

				// update the dependence relation
				if (SummariesEnv.v().isUsingCstCache()) {
					cstDepMap.add(ap, new Pair<MemLocInstnItem, BoolExpr>(item,
							expr));
				}

				BoolExpr instSub;
				long instSubStart = System.nanoTime();
				if (sub.isEq()) {
					long unstart = System.nanoTime();
					instSub = instEqType(termStr, t, p2Set);
					long unend = System.nanoTime();
					G.eqTime += (unend - unstart);
				} else if (sub.isLE()) {
					long unstart = System.nanoTime();
					instSub = instLeType(termStr, t, p2Set);
					long unend = System.nanoTime();
					G.leTime += (unend - unstart);
				} else {
					assert sub.isGE();
					long unstart = System.nanoTime();
					instSub = instGeType(termStr, t, p2Set);
					long unend = System.nanoTime();
					G.geTime += (unend - unstart);
				}
				long instSubend = System.nanoTime();
				G.instSubTime += (instSubend - instSubStart);

				if (SummariesEnv.v().isUsingSubCache()) {
					long unstart = System.nanoTime();
					BoolExpr tmp = subCache
							.get(new Trio<BoolExpr, BoolExpr, BoolExpr>(ret1, sub, instSub));
					if (tmp == null) {
						tmp = (BoolExpr) ret1.substitute(sub, instSub);
					}
					subCache.put(
							new Trio<BoolExpr, BoolExpr, BoolExpr>(ret1,
									sub, instSub), tmp);
					ret1 = tmp;
					long unend = System.nanoTime();
					G.subTime += (unend - unstart);
				} else {
					ret1 = (BoolExpr) ret1.substitute(sub, instSub);

				}
				// ret1 = (BoolExpr) ret1.Substitute(sub, instSub);
			}
			long instend = System.nanoTime();
			G.instTime += (instend - inststart);

			BoolExpr result = null;
			if (SummariesEnv.v().isUsingSimplifyCache()) {
				long simStart = System.nanoTime();
				result = simplifyCache.get(ret1);
				if (result == null) {
					result = (BoolExpr) ret1.simplify();
					simplifyCache.put(ret1, result);
				}
				long simEnd = System.nanoTime();
				G.simTime += (simEnd - simStart);
			} else {
				result = (BoolExpr) ret1.simplify();

			}

			if (SummariesEnv.v().isUsingCstCache()) {
				instnCache
						.add(item, new Pair<BoolExpr, BoolExpr>(expr, result));
			}
			// BoolExpr result = (BoolExpr) ret1.Simplify();

			return result;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static Map<BoolExpr, BoolExpr> extractTermUsingCache(Expr expr)
			throws Z3Exception {
		Map<BoolExpr, BoolExpr> ret = extractCache.get(expr);
		if (ret != null) {
			return ret;
		}
		ret = new HashMap<BoolExpr, BoolExpr>();

		// using toString as the key to map the same expr, buggy.

		if (expr.isEq() || expr.isLE() || expr.isGE()) {
			ret.put((BoolExpr)expr, (BoolExpr) expr);
		} else if (expr.isAnd() || expr.isOr()) {

			for (int i = 0; i < expr.getNumArgs(); i++) {
				assert expr.getArgs()[i] instanceof BoolExpr : "Not BoolExpr:"
						+ expr.getArgs()[i];
				BoolExpr sub = (BoolExpr) expr.getArgs()[i];
				Map<BoolExpr, BoolExpr> map = extractTermUsingCache(sub);
				ret.putAll(map);
			}
		}

		extractCache.put((BoolExpr)expr, ret);
		return ret;
	}

	// given an expr, extract all its sub terms for instantiating.
	public static void extractTerm(Expr expr, Map<BoolExpr, BoolExpr> map) {
		try {
			// using toString as the key to map the same expr, buggy.

			if (expr.isEq() || expr.isLE() || expr.isGE()) {
				map.put((BoolExpr)expr, (BoolExpr) expr);
				return;
			}

			if (expr.isAnd() || expr.isOr()) {

				for (int i = 0; i < expr.getNumArgs(); i++) {
					assert expr.getArgs()[i] instanceof BoolExpr : "Not BoolExpr:"
							+ expr.getArgs()[i];
					BoolExpr sub = (BoolExpr) expr.getArgs()[i];
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
			long unstart = System.nanoTime();
			ret = interCache.get(new Pair<BoolExpr, BoolExpr>(first,
					second));
			long unend = System.nanoTime();
			G.cst3 += (unend - unstart);
			if (ret != null) {
				return ret;
			}
		}

		if (SummariesEnv.v().disableCst())
			return trueExpr;
		try {
			long unstart = System.nanoTime();

			assert first != null : "Invalid constrait";
			assert second != null : "Invalid constrait";
			if (isFalse(first) || isFalse(second)) {
				ret = falseExpr;
			} else if (isTrue(first)) {
				ret = second;
			} else if (isTrue(second)) {
				ret = first;
			} else {
				ret = ctx.mkAnd(new BoolExpr[] { first, second });
			}

			if (SummariesEnv.v().isUsingInterCache()) {
				interCache.put(new Pair<BoolExpr, BoolExpr>(first, second), ret);
			}
			long unend = System.nanoTime();
			G.interTime += (unend - unstart);
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
			ret = unionCache.get(new Pair<BoolExpr, BoolExpr>(first, second));
			if (ret != null) {
				return ret;
			}
		}

		if (SummariesEnv.v().disableCst())
			return trueExpr;

		try {
			assert first != null : "Invalid constrait";
			assert second != null : "Invalid constrait";

			long unstart = System.nanoTime();
			if (isTrue(first) || isTrue(second)) {
				ret = trueExpr;
			} else if (isFalse(first)) {
				ret = second;
			} else if (isFalse(second)) {
				ret = first;
			} else {
				ret = ctx.mkOr(new BoolExpr[] { first, second });
			}

			if (SummariesEnv.v().isUsingUnionCache()) {
				unionCache.put(new Pair<BoolExpr, BoolExpr>(first,
						second), ret);
			}
			long unend = System.nanoTime();
			G.unionTime += (unend - unstart);
			return ret;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * generate subtyping constraint. if t has no subclass, then we generate
	 * type(o)=t. else we generate an interval for it: t_min <= type(o) <= t
	 * t_min is the least lower bound.
	 */
	// type(o) = T
	public static BoolExpr genEqType(HeapObject ho, jq_Class t) {
		Expr term = lift(ho);
		Expr cur = genTrue();
		int typeInt = Env.class2Term.get(t);
		try {
			if (ho instanceof AllocElem) {
				assert term.isInt();
				int srcInt = ((IntNum) term).getInt();
				int tgtInt = Env.getConstTerm4Class(t);
				if (srcInt == tgtInt)
					return genTrue();
				else
					return genFalse();
			} else if (ho instanceof AccessPath) {
				long tstart1 = System.nanoTime();
				cur = typeFun.apply(term);
				long tend1 = System.nanoTime();
				G.exprTime += (tend1 - tstart1);
			}
			long tstart = System.nanoTime();
			BoolExpr eq = ctx.mkEq(cur, ctx.mkInt(typeInt));
			long tend = System.nanoTime();
			G.exprTime += (tend - tstart);
			return eq;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (BoolExpr) cur;
	}

	// type(o) <= T
	public static BoolExpr genLeType(HeapObject ho, jq_Class t) {
		Expr term = lift(ho);
		Expr cur = genTrue();
		int typeInt = Env.class2Term.get(t);
		try {
			if (ho instanceof AllocElem) {
				assert term.isInt();
				int srcInt = ((IntNum) term).getInt();
				int tgtInt = Env.getConstTerm4Class(t);
				if (srcInt <= tgtInt)
					return genTrue();
				else
					return genFalse();
			} else if (ho instanceof AccessPath) {
				long tstart1 = System.nanoTime();
				cur = typeFun.apply(term);
				long tend1 = System.nanoTime();
				G.exprTime += (tend1 - tstart1);
			}
			long tstart = System.nanoTime();
			BoolExpr le = ctx.mkLe((IntExpr) cur, ctx.mkInt(typeInt));
			long tend = System.nanoTime();
			G.exprTime += (tend - tstart);
			return le;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (BoolExpr) cur;
	}

	// type(o) >= T
	public static BoolExpr genGeType(HeapObject ho, jq_Class t) {
		Expr term = lift(ho);
		Expr cur = genTrue();
		int typeInt = Env.class2Term.get(t);
		try {
			if (ho instanceof AllocElem) {
				int srcInt = ((IntNum) term).getInt();
				int tgtInt = Env.getConstTerm4Class(t);
				if (srcInt >= tgtInt)
					return genTrue();
				else
					return genFalse();
			} else if (ho instanceof AccessPath) {
				long tstart1 = System.nanoTime();
				cur = typeFun.apply(term);
				long tend1 = System.nanoTime();
				G.exprTime += (tend1 - tstart1);
			}
			
			long tstart = System.nanoTime();
			BoolExpr le = ctx.mkGe((IntExpr) cur, ctx.mkInt(typeInt));
			long tend = System.nanoTime();
			G.exprTime += (tend - tstart);
			return le;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (BoolExpr) cur;
	}
	
	public static Set<MemLocInstnSet> cacheSet = new HashSet<MemLocInstnSet>();

	// type(o) = T
	public static BoolExpr instEqType(String term, jq_Class t,
			MemLocInstnSet p2Set) {
		assert t != null;
		BoolExpr b = genFalse();
		if (p2Set.isEmpty())
			return genTrue();
		
		
		Pair<MemLocInstnSet, jq_Class> pair = new Pair<MemLocInstnSet, jq_Class>(
				p2Set, t);
		
		if (myInstCache.containsKey(pair)) {
			return myInstCache.get(pair);
		}
		
		System.out.println("size of pts: " + p2Set.keySet().size() + " cache:" + cacheSet.add(p2Set));
		for (AbsMemLoc ho : p2Set.keySet()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.get(ho);
				long tstart = System.nanoTime();
				BoolExpr eqExpr = genEqType((HeapObject) ho, t);
				long tend = System.nanoTime();
				G.genEqTime += (tend - tstart);
				
				long tstart1 = System.nanoTime();
				BoolExpr newCst = intersect(orgCst, eqExpr);
				long tend1 = System.nanoTime();
				G.cst1 += (tend1 - tstart1);
				if (isTrue(newCst))
					return trueExpr;
				
				long tstart2 = System.nanoTime();
				b = union(b, newCst);
				long tend2 = System.nanoTime();
				G.cst2 += (tend2 - tstart2);
			}
		}
		
		myInstCache.put(pair, b);
		return b;
	}

	// type(o) <= T
	public static BoolExpr instLeType(String term, jq_Class t,
			MemLocInstnSet p2Set) {
		assert t != null;

		BoolExpr b = genFalse();
		if (p2Set.isEmpty())
			return genTrue();

		for (AbsMemLoc ho : p2Set.keySet()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.get(ho);
				BoolExpr newCst = intersect(orgCst,
						genLeType((HeapObject) ho, t));
				if (isTrue(newCst))
					return trueExpr;

				b = union(b, newCst);
			}
		}
		return b;
	}

	// type(o) >= T
	public static BoolExpr instGeType(String term, jq_Class t,
			MemLocInstnSet p2Set) {
		assert t != null;

		BoolExpr b = genFalse();
		if (p2Set.isEmpty())
			return genTrue();

		for (AbsMemLoc ho : p2Set.keySet()) {
			if (ho instanceof HeapObject) {
				BoolExpr orgCst = p2Set.get(ho);
				BoolExpr newCst = intersect(orgCst,
						genGeType((HeapObject) ho, t));
				if (isTrue(newCst))
					return trueExpr;

				b = union(b, newCst);
			}
		}
		return b;
	}

	// hasType(o)= T
	public static BoolExpr hasEqType(P2Set p2Set, jq_Class t) {
		BoolExpr b = genFalse();
		if (p2Set.isEmpty())
			return genTrue();

		for (HeapObject ho : p2Set.keySet()) {
			BoolExpr orgCst = p2Set.get(ho);
			long tstart = System.nanoTime();
			BoolExpr eqExpr = genEqType(ho, t);
			long tend = System.nanoTime();
			G.genEqTime += (tend - tstart);
			BoolExpr newCst = intersect(orgCst, eqExpr);
			if (isTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	// T1 <= hasType(o) <=T
	public static BoolExpr hasIntervalType(P2Set p2Set, jq_Class t) {
		BoolExpr b = genFalse();
		if (p2Set.isEmpty())
			return genTrue();

		for (HeapObject ho : p2Set.keySet()) {
			BoolExpr orgCst = p2Set.get(ho);
			int minInt = Env.getMinSubclass(t);
			jq_Class subMin = Env.class2TermRev.get(minInt);
			assert subMin != null : minInt;

			BoolExpr le = genLeType(ho, t);
			BoolExpr ge = genGeType(ho, subMin);
			BoolExpr interval = intersect(le, ge);

			BoolExpr newCst = intersect(orgCst, interval);
			if (isTrue(newCst))
				return trueExpr;
			b = union(b, newCst);
		}
		return b;
	}

	/** lift: Convert heap object to term. */
	public static Expr lift(HeapObject ho) {
		Expr cur = genTrue();
		try {
			if (ho instanceof AllocElem) {
				// return the number of its class.
				AllocElem ae = (AllocElem) ho;
				jq_Type jType = ae.getAlloc().getType();
				// always true for jq_array.
				if (jType instanceof jq_Array)
					return (BoolExpr) cur;

				long tstart = System.nanoTime();
				cur = ctx.mkInt(Env.getConstTerm4Class((jq_Class) jType));
				long tend = System.nanoTime();
				G.liftTime += (tend - tstart);

			} else if (ho instanceof AccessPath) {
				AccessPath ap = (AccessPath) ho;
				String symbol = "";
				// if (ap.isSmashed()) {
				// int cnt = 1;
				// if (ap2Counter.get(ap) != null)
				// cnt = ap2Counter.get(ap) + 1;
				//
				// ap2Counter.put(ap, cnt);
				// symbol = "v" + ap.getId() + "s" + cnt;
				// } else {
				symbol = "v" + ap.getId();
				// }
				// put to map for unlifting later.
				term2Ap.put(symbol, ap);
				long tstart = System.nanoTime();
				cur = ctx.mkConst(symbol, ctx.getIntSort());
				long tend = System.nanoTime();
				G.liftTime += (tend - tstart);
			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return cur;
	}

	/** lift inverse: Convert term to heap object. */
	public static HeapObject liftInv(String term) {
		return term2Ap.get(term);
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
			Pair<BoolExpr, BoolExpr> pair = new Pair<BoolExpr, BoolExpr>(
					expr1, expr2);
			if (eqCache.containsKey(pair)) {
				return eqCache.get(pair);
			}
		}

		try {
			long tstart = System.nanoTime();
			BoolExpr e1;
			e1 = ctx.mkIff(expr1, expr2);
			BoolExpr e2 = ctx.mkNot(e1);
			solver.push();
			solver.add(e2);
			Status status = solver.check();
			solver.pop();
			long tend = System.nanoTime();
			G.equalsTime += (tend - tstart);


			if (status == Status.UNSATISFIABLE) {
				ret = true;
				if (SummariesEnv.v().isUsingEqCache()) {
					eqCache.put(new Pair<BoolExpr, BoolExpr>(expr1,
							expr2), ret);
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
					new Pair<BoolExpr, BoolExpr>(expr1, expr2),
					ret);
		}

		return ret;
	}
	
	// check if cst is a scala constraint, e.g, true, false
	public static boolean isScala(BoolExpr cst) {
		try {
			if (cst.isTrue() || cst.isFalse())
				return true;
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public static boolean isTrue(BoolExpr cst) {
		try {
			return cst.isTrue();
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public static boolean isFalse(BoolExpr cst) {
		try {
			return cst.isFalse();
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
