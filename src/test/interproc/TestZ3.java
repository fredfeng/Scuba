package test.interproc;

import java.util.HashMap;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
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
		 HashMap<String, String> cfg = new HashMap<String, String>();
         cfg.put("model", "true");
         try {
			Context ctx = new Context(cfg);
			
	        Solver solver = ctx.MkSolver();

			//define the uninterpreted function. type(o)=T
			FuncDecl typeFun = ctx.MkFuncDecl("type", ctx.IntSort(),
					ctx.IntSort());
			
	        Expr o = ctx.MkConst("o", ctx.IntSort());
	        //type(o)
	        Expr to = typeFun.Apply(o);
	        //type(o)=1
	        BoolExpr eq = ctx.MkEq(to, ctx.MkInt("1"));
	        
	        BoolExpr eq2 = ctx.MkEq(to, ctx.MkInt("1"));
	        
	        BoolExpr sim = ctx.MkAnd(new BoolExpr[] { eq, eq2 });
	        
	        solver.Assert(eq);
	        solver.Assert(eq2);
	        
	        

			
			System.out.println(eq);
			System.out.println(sim.Simplify());

			System.out.println(solver.Check());

			
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
