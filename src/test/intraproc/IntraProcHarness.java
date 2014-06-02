package test.intraproc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.CodeCache;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Main.HostedVM;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.helper.G;

/**
 * Harness to run intra-proc analysis
 * 
 * @author yufeng
 * 
 */
public class IntraProcHarness {

	// temporary entry point for intra-proc analysis.
	public static void main(String[] args) {
		HostedVM.initialize();
		HashSet<jq_Method> set = new HashSet<jq_Method>();

		// assign the class you want to analyze. ignore function calls.
		// String s = "test.intraproc.TestAssignment";
		// String s = "test.intraproc.TestRule1";
		// String s = "test.intraproc.TestRule2";
		// String s = "test.intraproc.TestRule3";
		// String s = "test.intraproc.TestRule4";
		// String s = "test.intraproc.TestRule6";
		String s = "test.intraproc.TestRule5";
		jq_Class c = (jq_Class) jq_Type.parseType(s);
		c.load();
		set.addAll(Arrays.asList(c.getDeclaredStaticMethods()));
		set.addAll(Arrays.asList(c.getDeclaredInstanceMethods()));

		IntraProcSumAnalysis p = new IntraProcSumAnalysis();
		for (Iterator<jq_Method> i = set.iterator(); i.hasNext();) {
			jq_Method m = (jq_Method) i.next();

			if (G.debug) {
				System.out
						.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				System.out.println("Handling the method: ");
				System.out.println(m);
			}

			Summary summary = SummariesEnv.v().getSummary(m);
			p.setSummary(summary);
			if (m.getBytecode() == null)
				continue;
			ControlFlowGraph cfg = CodeCache.getCode(m);

			if (G.debug) {
				System.out.println("*****************************************");
				System.out.println(cfg.fullDump());
				System.out.println("*****************************************");
			}

			p.analyze(cfg);

			// summary.dumpSummaryToFile();
			// summary.dumpAllMemLocsHeapToFile();
			summary.validate();

			if (G.debug) {
				System.out.println("Finish handling the method.");
				System.out
						.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$\n");
			}
			// break;
		}

	}

}
