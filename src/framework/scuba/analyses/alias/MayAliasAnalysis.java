package framework.scuba.analyses.alias;

import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.bddbddb.Rel.RelView;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;
import framework.scuba.domain.AllocElem;
import framework.scuba.utils.StringUtil;

/**
 * Analysis to perform may-alias queries on Pair(v1,v2) Such that v1 and v2
 * dereference to the same field. e.g., x = v1.f; v2.f = y.
 */
public class MayAliasAnalysis {

	protected ProgramRel relVValias;
	protected ProgramRel relMV;
	SummaryBasedAnalysis analysis;

	public MayAliasAnalysis(ProgramRel mv, ProgramRel alias,
			SummaryBasedAnalysis sum) {
		relVValias = alias;
		relMV = mv;
		analysis = sum;
	}

	public void run() {
		StringUtil
				.reportInfo("May Alias Experiment--------------------------------");
		if (!relVValias.isOpen())
			relVValias.load();

		if (!relMV.isOpen())
			relMV.load();

		RelView view = relVValias.getView();
		int bothNotEmpty = 0;
		int bothNotEmptyNotAlias = 0;
		int emptyQuery = 0;

		Iterable<Pair<Register, Register>> res = view.getAry2ValTuples();
		for (Pair<Register, Register> vv : res) {
			Register v1 = vv.val0;
			Register v2 = vv.val1;

			RelView viewMv1 = relMV.getView();
			viewMv1.selectAndDelete(1, v1);
			Iterable<jq_Method> m1It = viewMv1.getAry1ValTuples();
			jq_Method m1 = m1It.iterator().next();
			jq_Class cls1 = m1.getDeclaringClass();

			RelView viewMv2 = relMV.getView();
			viewMv2.selectAndDelete(1, v2);
			Iterable<jq_Method> m2It = viewMv2.getAry1ValTuples();
			jq_Method m2 = m2It.iterator().next();
			jq_Class cls2 = m2.getDeclaringClass();
			// long start2SCC = System.nanoTime();

			// StringUtil.reportSec("CHORD's time", startSCC, start2SCC);

			Set<AllocElem> p2Set1 = analysis.query(cls1, m1, v1);
			Set<AllocElem> p2Set2 = analysis.query(cls2, m2, v2);

			StringUtil.reportInfo("[mayAlias] " + v1 + "@" + m1);
			StringUtil.reportInfo("[mayAlias] " + v2 + "@" + m2);

			if (p2Set1.isEmpty() || p2Set2.isEmpty()) {
				StringUtil
						.reportInfo("[mayAlias]Result: unknown because one of them constains empty set.");
				emptyQuery++;
			} else {
				bothNotEmpty++;
				p2Set1.retainAll(p2Set2);
				if (p2Set1.isEmpty()) {
					StringUtil.reportInfo("[mayAlias]Result: YES. Not alias.");
					StringUtil.reportInfo("[mayAlias]P2Set of " + v1 + " : "
							+ p2Set1);
					StringUtil.reportInfo("[mayAlias]P2Set of " + v2 + " : "
							+ p2Set2);
					bothNotEmptyNotAlias++;
				} else {
					StringUtil.reportInfo("[mayAlias]Result: No. Still alias.");
					StringUtil.reportInfo("[mayAlias]P2Set of " + v1 + " : "
							+ p2Set1);
					StringUtil.reportInfo("[mayAlias]P2Set of " + v2 + " : "
							+ p2Set2);

				}
			}
		}

		StringUtil
				.reportInfo("[mayAlias]May Alias Result--------------------------------");
		StringUtil.reportInfo("[mayAlias]Total queries: " + view.size());
		StringUtil.reportInfo("[mayAlias]Non-empty Queries: " + bothNotEmpty);
		StringUtil.reportInfo("[mayAlias]Non-alias Queries(Exclude empty): "
				+ bothNotEmptyNotAlias);
		StringUtil.reportInfo("[mayAlias]Non-alias Queries(Include empty): "
				+ (bothNotEmptyNotAlias + emptyQuery));
	}
}
