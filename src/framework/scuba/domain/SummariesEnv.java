package framework.scuba.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;

/**
 * Global env to store all summaries of their methods. Singleton pattern.
 * Corresponding to 'Epsilon' in Figure 7.
 * 
 * @author yufeng
 * 
 */
public class SummariesEnv {

	private static SummariesEnv instance = new SummariesEnv();

	Map<jq_Method, Summary> summaries = new HashMap<jq_Method, Summary>();

	String[] blklist = {
			"fixAfterDeletion:(Ljava/util/TreeMap$Entry;)V@java.util.TreeMap",
			"" };

	public static SummariesEnv v() {
		return instance;
	}

	// use numbering to produce the heap and do the instantiation
	protected boolean useNumbering = false;

	// the number of contexts in an AllocElem
	// 0 means infinity
	protected int allocDepth = 1;

	// prop locals or not
	protected boolean propLocals = true;

	// ignore string
	protected boolean openBlklist = true;

	// cheating
	protected boolean cheating = false;

	// ignore string
	protected boolean ignoreString = true;

	// force to invoke garbage collector for abstract heap.
	protected boolean forceGc = false;

	// disable constraint instantiate.
	protected boolean disableCst = false;

	public boolean disableCst() {
		return disableCst;
	}

	public boolean openBlklist() {
		return openBlklist;
	}

	public boolean ignoreString() {
		return ignoreString;
	}

	public boolean forceGc() {
		return forceGc;
	}

	public boolean cheating() {
		return cheating;
	}

	public int getAllocDepth() {
		return allocDepth;
	}

	public void setAllocDepth(int depth) {
		allocDepth = depth;
	}

	public boolean useNumbering() {
		return useNumbering;
	}

	public void setUseNumbering() {
		useNumbering = true;
	}

	public void resetUseNumbering() {
		useNumbering = false;
	}

	public static void reset() {
		instance = new SummariesEnv();
	}

	public Summary getSummary(jq_Method meth) {
		return summaries.get(meth);
	}

	public Map<jq_Method, Summary> getSums() {
		return summaries;
	}

	public Summary removeSummary(jq_Method meth) {
		return summaries.remove(meth);
	}

	public Summary initSummary(jq_Method meth) {
		// for scc, it may exist.
		if (summaries.get(meth) != null)
			return summaries.get(meth);
		else {
			putSummary(meth, new Summary(meth));
			return summaries.get(meth);
		}
	}

	public Summary putSummary(jq_Method meth, Summary sum) {
		return summaries.put(meth, sum);
	}

	public boolean isInBlacklist(String blk) {
		return Arrays.asList(blklist).contains(blk);
	}

}
