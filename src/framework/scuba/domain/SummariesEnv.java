package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Trio;

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
		ALL, NOLOCAL, DOWNCAST, APPLOCAL, NOTHING;
	}

	public static enum FieldSmashLevel {
		REG, CTRLLENGTH, CTRLILENGTH, TYPESMASH, TYPECOMPSMASH;
	}

	public static SummariesEnv v() {
		return instance;
	}

	// the number of contexts in an AllocElem
	// 0 means infinity
	protected int allocDepth = 3;
	// dynamically control the depth
	protected boolean dynAlloc = false;

	// customize what to propagate
	// protected boolean propFilter = false;
	// the locals that we care about
	protected Set<Register> toProp = new HashSet<Register>();

	// all reachable methods
	protected Set<jq_Method> reachableMethods = new HashSet<jq_Method>();

	// all library methods
	protected Set<jq_Method> libMeths = new HashSet<jq_Method>();

	// alias pairs
	protected LinkedHashSet<Trio<jq_Method, Register, Register>> aliasPairs = new LinkedHashSet<Trio<jq_Method, Register, Register>>();

	// disable constraint instantiate.
	protected boolean disableCst = true;
	// we mark it as bad scc if its size greater than this number.
	public final int sccLimit = 30;
	// type filter
	public boolean useTypeFilter = true;
	public boolean useSubTypeFilter = true;
	// whether or not resolve default static access path in the final heap
	public boolean resolveFinalHeap = true;
	// a trick to avoid hanging in gigantic SCC
	protected boolean badMethodSkip = false;

	public FieldSmashLevel level = FieldSmashLevel.REG;
	// public FieldSmashLevel level = FieldSmashLevel.TYPESMASH;
	// public FieldSmashLevel level = FieldSmashLevel.TYPECOMPSMASH;

	// fix-point or not
	protected boolean useFixPoint = true;
	// when concluding the clinit's and main, use fix-point or not
	protected boolean topFixPoint = true;
	// whether or not propagate parameters
	protected boolean propParams = true;

	// which kind of local need to be propagated
	// protected PropType localType = PropType.APPLOCAL;
	// protected PropType localType = PropType.DOWNCAST;
	protected PropType localType = PropType.NOLOCAL;

	// protected PropType localType = PropType.NOTHING;
	// protected PropType localType = PropType.ALL;

	public PropType getLocalType() {
		return localType;
	}

	public boolean disableCst() {
		return disableCst;
	}

	public static void reset() {
		instance = new SummariesEnv();
	}

	public void sumAll(Set<AbsHeap> clinitHeaps, AbsHeap mainHeap) {
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

	public void addAliasPairs(jq_Method m, Register r1, Register r2) {
		Trio<jq_Method, Register, Register> trio = new Trio<jq_Method, Register, Register>(
				m, r1, r2);
		aliasPairs.add(trio);
	}

	public Set<Trio<jq_Method, Register, Register>> getAliasPairs() {
		return aliasPairs;
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
						.matches("getPrngAlgorithm:\\(\\)Ljava/lang/String;@java.*"))
			return true;

		return false;
	}
}
