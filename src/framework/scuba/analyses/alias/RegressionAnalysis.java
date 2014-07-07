package framework.scuba.analyses.alias;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.project.Config;
import chord.util.tuple.object.Trio;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.SummariesEnv;

public class RegressionAnalysis {
	
	SummaryBasedAnalysis analysis;
	
	LinkedList<String> resList = new LinkedList<String>();
	
	public RegressionAnalysis(SummaryBasedAnalysis sum) {
		analysis = sum;
		//read pre-stored result from work-dir
		String workdir = Config.workDirName;
		String resFile = workdir.concat("/result.txt");
		FileInputStream fstream;
		try {
			fstream = new FileInputStream(resFile);
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
			String strLine;
			while ((strLine = br.readLine()) != null)   {
			  resList.add(strLine);
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		assert resList.size() == SummariesEnv.v().getAliasPairs().size() : "Regression test fails. alias number unmatched.";
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
			String compare = "";
			if (intersection.isEmpty())
				compare = "alias(" + r1 + "," + r2 + ") false";
			else
				compare = "alias(" + r1 + "," + r2 + ") true";
			
			String org = resList.poll();
			assert org.equals(compare) : "Regression Test fails: alias(" + r1
					+ "," + r2 + ")";

		}
	}
}
