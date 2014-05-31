package test.intraproc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Dataflow.BBComparator;
import joeq.Compiler.Dataflow.IterativeSolver;
import joeq.Compiler.Dataflow.PriorityQueueSolver;
import joeq.Compiler.Dataflow.Problem;
import joeq.Compiler.Dataflow.Solver;
import joeq.Compiler.Dataflow.SortedSetSolver;
import joeq.Compiler.Quad.CodeCache;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Main.HostedVM;
import jwutil.graphs.EdgeGraph;
import jwutil.graphs.ReverseGraph;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;

/**
 * Harness to run intra-proc analysis
 * @author yufeng
 *
 */
public class IntraProcHarness {

    public static IntraProcSumAnalysis solve(ControlFlowGraph cfg) {
    	IntraProcSumAnalysis p = new IntraProcSumAnalysis();
        Solver s1 = new IterativeSolver();
        p.mySolver = s1;
        solve(cfg, s1, p);
        System.out.println("Finished solving IntraProcSumAnalysis.");
        return p;
    }
    
    private static void solve(ControlFlowGraph cfg, Solver s, Problem p) {
        s.initialize(p, new EdgeGraph(new ReverseGraph(cfg, Collections.singleton(cfg.exit()))));
        s.solve();
    }

    //temporary entry point for intra-proc analysis.
    public static void main(String[] args) {
        HostedVM.initialize();
        HashSet<jq_Method> set = new HashSet<jq_Method>();
        
        //assign the class you want to analyze. ignore function calls.
		String s = "test.intraproc.TestAssignment";
		jq_Class c = (jq_Class) jq_Type.parseType(s);
		c.load();
		set.addAll(Arrays.asList(c.getDeclaredStaticMethods()));
		set.addAll(Arrays.asList(c.getDeclaredInstanceMethods()));
            
		IntraProcSumAnalysis p = new IntraProcSumAnalysis();
        Solver s1 = new IterativeSolver();
//        Solver s2 = new SortedSetSolver(BBComparator.INSTANCE);
//        Solver s3 = new PriorityQueueSolver();
        for (Iterator<jq_Method> i = set.iterator(); i.hasNext(); ) {
            jq_Method m = (jq_Method) i.next();
            
            Summary summary = SummariesEnv.v().getSummary(m);
            p.setSummary(summary);
            if (m.getBytecode() == null) continue;
            System.out.println("Method "+m);
            ControlFlowGraph cfg = CodeCache.getCode(m);
            System.out.println(cfg.fullDump());
            solve(cfg, s1, p);
//            solve(cfg, s2, p);
//            solve(cfg, s3, p);
            Solver.dumpResults(cfg, s1);
//            Solver.compareResults(cfg, s1, s2);
//            Solver.compareResults(cfg, s2, s3);
        }
    }

}
