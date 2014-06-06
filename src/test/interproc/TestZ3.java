package test.interproc;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Z3Exception;

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

		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
