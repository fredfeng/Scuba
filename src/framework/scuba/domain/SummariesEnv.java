package framework.scuba.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import chord.bddbddb.Rel.RelView;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Pair;
import framework.scuba.helper.G;
import framework.scuba.utils.StringUtil;

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

	// to handle local's pt set
	// when the context depth reaches the bound, just fill the (local, alloc)
	// into this map instead of propagating those locals upwards
	Map<LocalVarElem, Set<AllocElem>> localsToAlloc = new HashMap<LocalVarElem, Set<AllocElem>>();

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
	protected boolean cheating = true;

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

	public Set<AllocElem> cacheLocals(LocalVarElem local, AllocElem allocT) {
		Set<AllocElem> ret = localsToAlloc.get(local);
		if (ret == null) {
			ret = new HashSet<AllocElem>();
			localsToAlloc.put(local, ret);
		}
		ret.add(allocT);
		return ret;
	}
}
