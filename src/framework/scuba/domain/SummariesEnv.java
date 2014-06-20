package framework.scuba.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

	final protected Map<jq_Method, Summary> summaries = new HashMap<jq_Method, Summary>();

	protected SumConclusion finalSum;

	String[] blklist = {
			"fixAfterDeletion:(Ljava/util/TreeMap$Entry;)V@java.util.TreeMap",
			"" };

	public static SummariesEnv v() {
		return instance;
	}

	// the number of contexts in an AllocElem
	// 0 means infinity
	protected int allocDepth = 1;

	// prop locals or not
	protected boolean propLocals = false;

	// prop statics or not
	protected boolean propStatics = true;

	// ignore string
	protected boolean openBlklist = false;

	// cheating
	protected boolean cheating = false;

	// ignore string
	protected boolean ignoreString = false;

	// force to invoke garbage collector for abstract heap.
	protected boolean forceGc = false;

	// disable constraint instantiate.
	protected boolean disableCst = false;

	// whether use cache for instantiating AccessPath
	protected boolean useMemLocCache = true;

	// whether use cache for constraint instantiation
	protected boolean useCstCache = true;

	// whether use cache for extracting terms
	protected boolean useExtractCache = true;

	// whether use cache for constraint simplification
	protected boolean useSimplifyCache = true;

	// whether use cache for constraint union operation
	protected boolean useUnionCache = true;

	// whether use cache for constraint intersection operation
	protected boolean useInterCache = true;

	// whether use cache for substitution operation of constraints
	protected boolean useSubCache = true;

	// whether use equivalence checking cache
	protected boolean useEqCache = false;

	// this is a fantastic way to efficiently skip the instantiation for those
	// callees that we can magically predict that they will not change the
	// caller's heap
	protected boolean smartSkip = true;

	// a fine-grained smart skip for instantiating edges
	protected boolean moreSmartSkip = true;

	protected boolean typeSmashing = false;

	protected boolean clearLocals = false;

	// fix-point or not
	protected boolean useFixPoint = true;

	public boolean cleanLocals() {
		return clearLocals;
	}

	public void enableSmartSkip() {
		smartSkip = true;
	}

	public void disableSmartSkip() {
		smartSkip = false;
	}

	public boolean isUsingSmartSkip() {
		return smartSkip;
	}

	public void enableUnionCache() {
		useUnionCache = true;
	}

	public void disableUnionCache() {
		useUnionCache = false;
	}

	public boolean isUsingUnionCache() {
		return useUnionCache;
	}

	public void enableSubCache() {
		useSubCache = true;
	}

	public void disableSubCache() {
		useSubCache = false;
	}

	public boolean isUsingSubCache() {
		return useSubCache;
	}

	public void enableInterCache() {
		useInterCache = true;
	}

	public void disableInterCache() {
		useInterCache = false;
	}

	public boolean isUsingInterCache() {
		return useInterCache;
	}

	public void enableSimplifyCache() {
		useSimplifyCache = true;
	}

	public void disableSimplifyCache() {
		useSimplifyCache = false;
	}

	public boolean isUsingSimplifyCache() {
		return useSimplifyCache;
	}

	public void enableMemLocCache() {
		useMemLocCache = true;
	}

	public void disableMemLocCache() {
		useMemLocCache = false;
	}

	public boolean isUsingMemLocCache() {
		return useMemLocCache;
	}

	public void enableCstCache() {
		useCstCache = true;
	}

	public void disableCstCache() {
		useCstCache = false;
	}

	public boolean isUsingCstCache() {
		return useCstCache;
	}

	public void enableExtractCache() {
		useExtractCache = true;
	}

	public void disableExtractCache() {
		useExtractCache = false;
	}

	public boolean isUsingExtractCache() {
		return useExtractCache;
	}

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

	public static void reset() {
		instance = new SummariesEnv();
	}

	public void sumAll(Set<AbstractHeap> clinitHeaps, AbstractHeap mainHeap) {
		finalSum = new SumConclusion(clinitHeaps, mainHeap);
		finalSum.sumAllHeaps();
	}

	public SumConclusion getFinalSum() {
		return finalSum;
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
