package test.interproc;

import java.util.HashSet;
import java.util.Set;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Solver;
import com.microsoft.z3.StringSymbol;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_lbool;

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
			
	        Solver solver = ctx.MkSolver();

			//define the uninterpreted function. type(o)=T
			FuncDecl typeFun = ctx.MkFuncDecl("type", ctx.IntSort(),
					ctx.IntSort());
			
	        Expr o = ctx.MkConst("o", ctx.IntSort());
	        //type(o)
	        IntExpr to = (IntExpr)typeFun.Apply(o);
	        //type(o)=1
	        BoolExpr eq = ctx.MkEq(to, ctx.MkInt("1"));
	        
	        BoolExpr eq2 = ctx.MkEq(to, ctx.MkInt("1"));
	        
	        BoolExpr sim = ctx.MkAnd(new BoolExpr[] { eq, eq2 });
	        
	        solver.Assert(eq);
	        solver.Assert(eq2);
	        
			System.out.println(eq);
			System.out.println(sim.Simplify());

			System.out.println(solver.Check());
			
			//////test assumption in z3
			System.out.println("Test assumption in Z3...");
	        BoolExpr assume = ctx.MkLe(to, ctx.MkInt("5"));

	        BoolExpr[] assumptions = new BoolExpr[] { assume };
	        
	        BoolExpr eq3 = ctx.MkEq(to, ctx.MkInt("6"));
	        BoolExpr lt = ctx.MkLe(to, ctx.MkInt("5"));
	        
	        BoolExpr sim2 = ctx.MkOr(new BoolExpr[] { eq3, lt });
	        
	        BoolExpr sim3 = ctx.MkAnd(new BoolExpr[] { eq3, sim2 });


			System.out.println(sim2);
			System.out.println(sim2.Simplify());

			System.out.println(solver.Check());
			
			
			//something trivial.
			BoolExpr trueExpr = ctx.MkBool(true);
			BoolExpr falseExpr = ctx.MkBool(false);
			
			System.out.println(trueExpr);
			System.out.println(falseExpr);
			
			//perform cloning.
			System.out.println("Cloning......" + eq3);
	        BoolExpr clone = ctx.MkOr(new BoolExpr[] { eq3, eq3 });
	        //eq3 and clone are different instances, but share the same boolValue.
			System.out.println("After Cloning......" + eq3
					+ eq3.BoolValue().equals(clone.BoolValue()));
			
			System.out.println("Bool:" + eq3.BoolValue().toInt());
			

			System.out.println("Check equivalent-------------");
	        BoolExpr a = (BoolExpr)ctx.MkConst("A", ctx.BoolSort());
	        BoolExpr b = (BoolExpr)ctx.MkConst("B", ctx.BoolSort());
	        BoolExpr e1 = ctx.MkOr(new BoolExpr[]{a,b});
	        BoolExpr e2 = ctx.MkAnd(new BoolExpr[] {e1, a});
	        
	        BoolExpr e3 = ctx.MkEq(a, e2);
	        BoolExpr e4 = ctx.MkNot(e3);
	        solver.Assert(e4);
	        System.out.println(solver.Check());
	        
	        System.out.println(trueExpr.BoolValue() == Z3_lbool.Z3_L_TRUE);
	        System.out.println(falseExpr.BoolValue() == Z3_lbool.Z3_L_FALSE);
	        
	        //test substitute.
	        System.out.println("Test substitution..." + sim3);
	        for(int i = 0; i < sim3.NumArgs(); i++) 
	        	System.out.println(sim3.Args()[i]);
	        
	        System.out.println("Sub:" + sim3.Substitute(lt, trueExpr));
	        
	        System.out.println("Extracting..." + sim3);
	        Set<Expr> set = new HashSet<Expr>();
	        extractTerm(sim3, set);
	        System.out.println("Set: " + set);


		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	void extractTerm(Expr expr, Set<Expr> set) {
        try {
			for(int i = 0; i < expr.NumArgs(); i++) {
				Expr sub = expr.Args()[i];
				if(sub.IsAnd() || sub.IsOr())
					extractTerm(sub, set);
				else {
					Expr o = sub.Args()[0].Args()[0];
					System.out.println("Term: " + sub);
					System.out.println("Term: " + o.toString()
							+ ":::" + sub.Args()[0] + "||" + sub.Args()[1]
							+ "-->" + ((IntNum) sub.Args()[1]).Int());

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
