package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Trio;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.SummariesEnv;

public class RegressionAnalysis {
	
	SummaryBasedAnalysis analysis;
	
	public RegressionAnalysis(SummaryBasedAnalysis sum) {
		analysis = sum;
	}

	public void run() {
		for (Trio<jq_Method, Register, Register> trio : SummariesEnv.v()
				.getAliasPairs()) {
			jq_Method m = trio.val0;
			Register r1 = trio.val1;
			Register r2 = trio.val2;
			jq_Class clz = m.getDeclaringClass();

			Set<AllocElem> p2Set1 = analysis.query(clz, m, r1);
			Set<AllocElem> p2Set2 = analysis.query(clz, m, r2);

			Set<AllocElem> intersection = new HashSet<AllocElem>(p2Set1);
			intersection.retainAll(p2Set2);
			if (intersection.isEmpty())
				System.out.println("alias check(" + r1 + "," + r2 + ") false");
			else
				System.out.println("alias check(" + r1 + "," + r2 + ") true");

		}
	}
}
