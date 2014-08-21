package test.interproc;

import java.util.HashSet;
import java.util.Set;

import com.microsoft.z3.ApplyResult;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Goal;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Params;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Tactic;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_lbool;

import framework.scuba.utils.StringUtil;

public class TestZ3 {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("Starting Z3 for Scuba...");
		TestZ3 p = new TestZ3();
		p.run();
	}
	
	void run() {
//		 HashMap<String, String> cfg = new HashMap<String, String>();
//         cfg.put("model", "true");
         try {
			Context ctx = new Context();
//			for(int i = 0; i < ctx.NumTactics(); i++)
//				System.out.println("Tactic: " + ctx.TacticNames()[i]);
			
//			Solver solver = ctx.MkTactic("sat").Solver();
	        Goal g4 = ctx.mkGoal(true, false, false);
			
	        Solver solver = ctx.mkSolver();
	        
	        Params solver_params = ctx.mkParams();
	        solver_params.add("ignore_solver1", true);
	        solver.setParameters(solver_params);
	        
			//define the uninterpreted function. type(o)=T
			FuncDecl typeFun = ctx.mkFuncDecl("type", ctx.mkIntSort(),
					ctx.mkIntSort());
			
	        Expr o = ctx.mkConst("o", ctx.mkIntSort());
	        
	        //type(o)
	        IntExpr to = (IntExpr)typeFun.apply(o);
	        //type(o)=1
	        BoolExpr eq = ctx.mkEq(to, ctx.mkInt("1"));
	        
	        BoolExpr eq2 = ctx.mkGt(to, ctx.mkInt("2"));
	        
	        BoolExpr eq4 = ctx.mkEq(ctx.mkInt("3"), ctx.mkInt("5"));
	        
	        BoolExpr sim = ctx.mkAnd(new BoolExpr[] { eq, eq2 });

			System.out.println("Before simplify: " + sim);

			solver.reset();
			long tstart = System.nanoTime();
			for (int i = 0; i < 100000; i++) {
//		        BoolExpr eq22 = ctx.MkGt(to, ctx.MkInt("2"));

//				solver.push();
//				solver.add(sim);
//				Status st = solver.check();
//				assert st == Status.UNSATISFIABLE;
//				solver.pop();
				g4.add(sim);
				ApplyResult ar = ApplyTactic(ctx, ctx.mkTactic("smt"), g4);
				g4.reset();
			}
//			long svend = System.nanoTime();
//			StringUtil.reportSec("MKint time-------: ", svstart, svend);
//			g4.Assert(sim);

//			ApplyResult ar = ApplyTactic(ctx, ctx.mkTactic("smt"), g4);
			long tend = System.nanoTime();
			StringUtil.reportSec("Tacitc time-------: ", tstart, tend);
			
			long simstart = System.nanoTime();
			System.out.println(sim.simplify());
			long simend = System.nanoTime();
			StringUtil.reportSec("Simplify time-------: ", simstart, simend);
			
			long resetstart = System.nanoTime();
			solver.reset();
			long resetend = System.nanoTime();
			StringUtil.reportSec("Reset time-------: ", resetstart, resetend);

			//////test assumption in z3
			System.out.println("Test assumption in Z3...");
	        BoolExpr assume = ctx.mkLe(to, ctx.mkInt("5"));

	        BoolExpr[] assumptions = new BoolExpr[] { assume };
	        
	        BoolExpr eq3 = ctx.mkEq(to, ctx.mkInt("6"));
	        BoolExpr lt = ctx.mkLe(to, ctx.mkInt("5"));
	        BoolExpr lt2 = ctx.mkLe(to, ctx.mkInt("54"));

	        BoolExpr sim2 = ctx.mkOr(new BoolExpr[] { eq3, lt });

	        BoolExpr sim3 = ctx.mkAnd(new BoolExpr[] { eq3, sim2 });


			System.out.println(sim2);
			System.out.println(sim2.simplify());

			System.out.println(solver.check());
			
			
			//something trivial.
			BoolExpr trueExpr = ctx.mkBool(true);
			BoolExpr falseExpr = ctx.mkBool(false);
			
			System.out.println(trueExpr);
			System.out.println(falseExpr);
			
			//perform cloning.
			System.out.println("Cloning......" + eq3);
			
	        BoolExpr clone = ctx.mkOr(new BoolExpr[] { eq3, eq3 });
	        //eq3 and clone are different instances, but share the same boolValue.
			System.out.println("After Cloning......" + eq3
					+ eq3.getBoolValue().equals(clone.getBoolValue()));
			
			System.out.println("Bool:" + eq3.getBoolValue().toInt());
			

			System.out.println("Check equivalent-------------");
	        BoolExpr a = (BoolExpr)ctx.mkConst("A", ctx.getBoolSort());
	        BoolExpr b = (BoolExpr)ctx.mkConst("B", ctx.getBoolSort());
			long orstart = System.nanoTime();
	        BoolExpr e1 = ctx.mkOr(new BoolExpr[]{a,b});
			long orend = System.nanoTime();
			StringUtil.reportSec("Or time------: ", orstart, orend);

			
			long andstart = System.nanoTime();
	        BoolExpr e2 = ctx.mkAnd(new BoolExpr[] {e1, a});
			long andend = System.nanoTime();
			StringUtil.reportSec("And time------: ", andstart, andend);

			ApplyResult ar2 = ApplyTactic(ctx, ctx.mkTactic("smt"), g4);
			long t2end = System.nanoTime();
			
	        System.out.println(trueExpr.getBoolValue() == Z3_lbool.Z3_L_TRUE);
	        System.out.println(falseExpr.getBoolValue() == Z3_lbool.Z3_L_FALSE);
	        
	        //test substitute.
	        System.out.println("Test substitution..." + sim3);
	        for(int i = 0; i < sim3.getNumArgs(); i++) 
	        	System.out.println(sim3.getArgs()[i]);
	        
			long substart = System.nanoTime();
	        System.out.println("Sub:" + sim3.substitute(lt, lt2));
			long subend = System.nanoTime();
			StringUtil.reportSec("Sub time------: ", substart, subend);
	        System.out.println("Sub2:" + sim3);

	        System.out.println("Extracting..." + sim3);
	        Set<Expr> set = new HashSet<Expr>();
	        extractTerm(sim3, set);
	        System.out.println("Set: " + set);


		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	ApplyResult ApplyTactic(Context ctx, Tactic t, Goal g) throws Z3Exception {
//		System.out.println("\nGoal: " + g);

		ApplyResult res = t.apply(g);
//		System.out.println("Application result: " + res);

		Status q = Status.UNKNOWN;
		for (Goal sg : res.getSubgoals())
			if (sg.isDecidedSat())
				q = Status.SATISFIABLE;
			else if (sg.isDecidedUnsat())
				q = Status.UNSATISFIABLE;

		switch (q) {
		case UNKNOWN:
//			System.out.println("Tactic result: Undecided");
			break;
		case SATISFIABLE:
//			System.out.println("Tactic result: SAT");
			break;
		case UNSATISFIABLE:
//			System.out.println("Tactic result: UNSAT");
			break;
		}

		return res;
	}
	 
	void extractTerm(Expr expr, Set<Expr> set) {
        try {
			for(int i = 0; i < expr.getNumArgs(); i++) {
				Expr sub = expr.getArgs()[i];
				if(sub.isAnd() || sub.isOr())
					extractTerm(sub, set);
				else {
					Expr o = sub.getArgs()[0].getArgs()[0];
					System.out.println("Term: " + sub);
					System.out.println("Term: " + o.toString()
							+ ":::" + sub.getArgs()[0] + "||" + sub.getArgs()[1]
							+ "-->" + ((IntNum) sub.getArgs()[1]).getInt());

					set.add(sub);
				}

			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//prove (A or B) and A  = A
	boolean checkEq(Expr expr1, Expr expr2) {
		return expr1.equals(expr2);
	}

}
