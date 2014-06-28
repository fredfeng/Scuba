package framework.scuba.domain;

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
		ALL, NOLOCAL, DOWNCAST, APPLOCAL, NOTHING, NOALLOC;
	}

	public static enum FieldSmashLevel {
		LOW, MED, HIGH, CONTROL, ICONTROL;
	}

	public static SummariesEnv v() {
		return instance;
	}

	// the number of contexts in an AllocElem
	// 0 means infinity
	protected int allocDepth = 2;
	// dynamically control the depth
	protected boolean dynAlloc = true;

	// customize what to propagate
	// protected boolean propFilter = false;
	// the locals that we care about
	protected Set<Register> toProp = new HashSet<Register>();

	// all reachable methods
	protected Set<jq_Method> reachableMethods = new HashSet<jq_Method>();

	// all library methods
	protected Set<jq_Method> libMeths = new HashSet<jq_Method>();

	// cheating
	protected boolean cheating = true;
	// ignore string
	protected boolean ignoreString = false;

	// force to invoke garbage collector for abstract heap.
	protected boolean forceGc = false;
	// disable constraint instantiate.
	protected boolean disableCst = true;
	// we mark it as bad scc if its size greater than this number.
	public final int sccLimit = 30;

	// this is the sound way to do smashing
	public boolean markSmashedFields = false;
	// this is used for instantiating the locations
	public boolean instnSmashedAPs = false;
	// whether use cache for instantiating AccessPath
	public boolean useMemLocInstnCache = true;
	// whether use cache for constraint instantiation
	public boolean useCstInstnCache = true;
	// whether use cache for extracting terms
	public boolean useExtractCache = true;
	// whether use cache for constraint simplification
	public boolean useSimplifyCache = true;
	// whether use cache for constraint union operation
	public boolean useUnionCache = true;
	// whether use cache for constraint intersection operation
	public boolean useInterCache = true;
	// whether use cache for substitution operation of constraints
	public boolean useSubCache = true;
	// whether use equivalence checking cache
	public boolean useEqCache = true;
	// this is a fantastic way to efficiently skip the instantiation for those
	// callees that we can magically predict that they will not change the
	// caller's heap
	public boolean smartSkip = true;
	// a fine-grained smart skip for instantiating edges
	public boolean moreSmartSkip = true;
	// when dbging SCC use this
	public boolean jump = true;
	// a trick to avoid hanging in gigantic SCC (the remove() method)
	protected boolean badMethodSkip = true;

	// type smashing for fields (imprecise) [DO NOT use this!]
	// public FieldSmashLevel level = FieldSmashLevel.LOW;
	// public FieldSmashLevel level = FieldSmashLevel.MED;
	// public FieldSmashLevel level = FieldSmashLevel.HIGH;
	// public FieldSmashLevel level = FieldSmashLevel.CONTROL;
	public FieldSmashLevel level = FieldSmashLevel.ICONTROL;
	public int smashLength = 3;

	// clear locals in the summary
	protected boolean clearLocals = false;

	// fix-point or not
	protected boolean useFixPoint = true;
	// when concluding the clinit's and main, use fix-point or not
	protected boolean topFixPoint = false;

	// whether or not propagate parameters
	protected boolean propParams = true;

	// which kind of local need to be propagated, e.g. downcast, all locals in
	// app, etc.
	protected PropType localType = PropType.APPLOCAL;

	// protected PropType localType = PropType.DOWNCAST;
	// protected PropType localType = PropType.NOLOCAL;
	// protected PropType localType = PropType.NOALLOC;
	// protected PropType localType = PropType.NOTHING;
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

	public Set<jq_Method> getLibMeths() {
		return libMeths;
	}

	public void setLibMeths(Set<jq_Method> libMeths) {
		this.libMeths = libMeths;
	}

	public boolean isStubMethod(String signature) {
		if (signature.matches("^equals:\\(Ljava/lang/Object;\\)Z@java.*")
				|| signature.matches("equals:(Ljava/lang/Object;)Z@sun.*")
				|| signature.matches("^hashCode:\\(\\)I@java.*")
				|| signature.matches("^hashCode:\\(\\)I@sun.*")
				|| signature
						.matches("implPut:\\(Ljava/lang/Object;Ljava/lang/Object;\\)Ljava/lang/Object;@java.*")
				|| signature
						.matches("^putAllForCreate:\\(Ljava/util/Map;\\)V@java.*")
				|| signature
						.matches("getPrngAlgorithm:\\(\\)Ljava/lang/String;@java.*")
		// || signature
		// .matches("^remove:\\(Ljava/lang/Object;\\)Z@java.*")
		// || signature
		// .matches("^removeAll:\\(Ljava/util/Collection;\\)Z@java.*")
		// || signature
		// .matches("^toString:\\(\\)Ljava/lang/String;@sun.*")
		// || signature
		// .matches("^toString:\\(\\)Ljava/lang/String;@java.*")
		// || signature.matches("^rotateRight:\\(Ljava/util/TreeMap.*")
		// || signature.matches("^rotateLeft:\\(Ljava/util/TreeMap.*")
		// || signature.matches("^hasMoreElements:\\(\\)Z@java.*")
		// || signature.matches("^getDefaultPRNG.*")
		// || signature
		// .matches("^addAllForTreeSet:\\(Ljava/util/SortedSet;Ljava/lang/Object;\\)V@java.*")
		// || signature
		// .matches("^addAll:\\(Ljava/util/Collection;\\)Z@java.*")
		// || signature.matches("^clone:\\(\\)Ljava/lang/Object;@java.*")
		// || signature.matches("^clone:\\(\\)Ljava/lang/Object;@sun.*")
		// just for speeding up debugging.
		// || signature.matches("^getResource.*")
		// || signature.matches("^checkCodeSigning:.*")
		// || signature.matches("^checkTLSServer:.*")
		// || signature.matches("^checkNetscapeCertType.*")
		// || signature.matches("^getExtensionValue:.*")
		// || signature.matches("^getCriticalExtensionOIDs.*")
		// || signature.matches("^getCriticalExtensionOIDs.*")
		// || signature.matches("^isNonEuroLangSupported.*")
		// || signature.matches("^createLocaleList.*")
		// || signature.matches("^access$000:\\(\\).*sun.*")
		// || signature.matches("<clinit>:\\(\\)V@sun.*")
		// || signature.matches("<clinit>:\\(\\)V@javax.*")
		// || signature.matches("^hasNext:\\(\\)Z@java")
		)
			return true;

		return false;
	}
}
