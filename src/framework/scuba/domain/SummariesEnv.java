package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;


/**
 * Global env to store all summaries of their methods. 
 * Singleton pattern.
 * Corresponding to 'Upsilon' in Figure 7.
 * @author yufeng
 *
 */
public class SummariesEnv {
	   
	private static SummariesEnv instance = new SummariesEnv();

	Map<jq_Method, Summary> summaries = new HashMap<jq_Method, Summary>();

	public static SummariesEnv v() {
		return instance;
	}

	public static void reset() {
		instance = new SummariesEnv();
	}

	public Summary getSummary(jq_Method meth) {
		return summaries.get(meth);
	}
	
	public Summary initSummary(jq_Method meth) {
		//for scc, it may exist.
		if(summaries.get(meth) != null)
			return summaries.get(meth);
		else {
			putSummary(meth, new Summary(meth));
			return summaries.get(meth);
		}
	}

	public Summary putSummary(jq_Method meth, Summary sum) {
		return summaries.put(meth, sum);
	}
}
