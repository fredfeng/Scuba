package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Quad;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.SummariesEnv;

public class RegressionAnalysis {
	
	SummaryBasedAnalysis analysis;
	
	LinkedList<String> resList = new LinkedList<String>();
	
	public RegressionAnalysis(SummaryBasedAnalysis sum) {
		analysis = sum;
	}

	public void run() {
		for (Quad<jq_Method, Register, Register, Boolean> quad : SummariesEnv.v()
				.getAliasPairs()) {
			jq_Method m = quad.val0;
			Register r1 = quad.val1;
			Register r2 = quad.val2;
			boolean flag = quad.val3;
			jq_Class clz = m.getDeclaringClass();

			Set<AllocElem> p2Set1 = analysis.query(clz, m, r1);
			Set<AllocElem> p2Set2 = analysis.query(clz, m, r2);

			Set<AllocElem> intersection = new HashSet<AllocElem>(p2Set1);
			intersection.retainAll(p2Set2);
			
			if (flag) {
				assert !intersection.isEmpty() : r1 + " and " + r2
						+ " is alias!";
			} else {//notAlias assertion.
				assert intersection.isEmpty() : r1 + " and " + r2
				+ " is NOT alias! " + m;
			}
		}
		
		System.out.println("Regression Test success!");
	}
}
