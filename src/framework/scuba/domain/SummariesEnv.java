package framework.scuba.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;

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

	public static enum PropType {
		ALL, NOLOCAL, DOWNCAST, APPLOCAL;
	}

	public static enum FieldSmashLevel {
		LOW, MED, HIGH, CONTROL;
	}

	String[] blklist = { "remove:(Ljava/lang/Object;)Z@java.util.AbstractCollection" };

	public static SummariesEnv v() {
		return instance;
	}

	// the number of contexts in an AllocElem
	// 0 means infinity
	protected int allocDepth = 1;

	// customize what to propagate
	// protected boolean propFilter = false;
	// the locals that we care about
	protected Set<Register> toProp = new HashSet<Register>();

	// all reachable methods
	protected Set<jq_Method> reachableMethods = new HashSet<jq_Method>();

	// ignore string
	protected boolean openBlklist = false;
	// cheating
	protected boolean cheating = false;
	// ignore string
	protected boolean ignoreString = false;

	// force to invoke garbage collector for abstract heap.
	protected boolean forceGc = false;
	// disable constraint instantiate.
	protected boolean disableCst = true;
	// we mark it as bad scc if its size greater than this number.
	public final int sccLimit = 30;

	// whether use cache for instantiating AccessPath
	protected boolean useMemLocInstnCache = true;
	// whether use cache for constraint instantiation
	protected boolean useCstInstnCache = true;
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
	protected boolean useEqCache = true;
	// this is a fantastic way to efficiently skip the instantiation for those
	// callees that we can magically predict that they will not change the
	// caller's heap
	protected boolean smartSkip = true;
	// a fine-grained smart skip for instantiating edges
	protected boolean moreSmartSkip = true;
	// a trick to avoid hanging in gigantic SCC (the remove() method)
	protected boolean badMethodSkip = true;

	// type smashing for fields (imprecise) [DO NOT use this!]
	// public FieldSmashLevel level = FieldSmashLevel.LOW;
	// public FieldSmashLevel level = FieldSmashLevel.MED;
	// public FieldSmashLevel level = FieldSmashLevel.HIGH;
	public FieldSmashLevel level = FieldSmashLevel.CONTROL;
	public int smashLength = 3;

	// clear locals in the summary
	protected boolean clearLocals = false;

	// fix-point or not
	protected boolean useFixPoint = true;
	// when concluding the clinit's and main, use fix-point or not
	protected boolean topFixPoint = true;

	// which kind of local need to be propagated, e.g. downcast, all locals in
	// app, etc.
	// protected PropType localType = PropType.APPLOCAL;
	protected PropType localType = PropType.DOWNCAST;

	// protected PropType localType = PropType.NOLOCAL;
	// protected PropType localType = PropType.ALL;

	public PropType getLocalType() {
		return localType;
	}

	public boolean useClearLocals() {
		return clearLocals;
	}

	public boolean isUsingSmartSkip() {
		return smartSkip;
	}

	public boolean isUsingEqCache() {
		return useEqCache;
	}

	public boolean isUsingUnionCache() {
		return useUnionCache;
	}

	public boolean isUsingSubCache() {
		return useSubCache;
	}

	public boolean isUsingInterCache() {
		return useInterCache;
	}

	public boolean isUsingSimplifyCache() {
		return useSimplifyCache;
	}

	public boolean isUsingMemLocCache() {
		return useMemLocInstnCache;
	}

	public boolean isUsingCstCache() {
		return useCstInstnCache;
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

	public void addPropSet(Register v) {
		toProp.add(v);
	}

	public void addAllPropSet(Set<Register> v) {
		toProp.addAll(v);
	}

	public boolean toProp(Register v) {
		return toProp.contains(v);
	}

	public Set<Register> getProps() {
		return toProp;
	}

	public Set<jq_Method> getReachableMethods() {
		return reachableMethods;
	}

	public void setReachableMethods(Set<jq_Method> reachableMethods) {
		this.reachableMethods = reachableMethods;
	}
}
