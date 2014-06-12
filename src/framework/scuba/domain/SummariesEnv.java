package framework.scuba.domain;

import java.util.HashMap;
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

	protected ProgramRel relCHA;

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

	public void setCHA(ProgramRel cha) {
		relCHA = cha;
	}

	public Set<Pair<jq_Reference, jq_Method>> loadInheritMeths(jq_Method m,
			jq_Class statType) {
		if (!relCHA.isOpen())
			relCHA.load();
		RelView view = relCHA.getView();
		view.selectAndDelete(0, m);
		Iterable<Pair<jq_Reference, jq_Method>> res = view.getAry2ValTuples();
		Set<Pair<jq_Reference, jq_Method>> pts = SetUtils.newSet(view.size());
		// no filter, add all
		if (statType == null) {
			for (Pair<jq_Reference, jq_Method> inst : res)
				pts.add(inst);
		} else {
			for (Pair<jq_Reference, jq_Method> inst : res) {
				if (inst.val0 instanceof jq_Array)
					continue;
				jq_Class clz = (jq_Class) inst.val0;
				// add the one that extents statType, no include itself.
				if (clz.extendsClass(statType) && !clz.equals(statType))
					pts.add(inst);
			}
		}
		if (G.tuning)
			StringUtil.reportInfo("resolve callee: size: " + pts.size() + ":"
					+ pts);
		return pts;
	}
}
