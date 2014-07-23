package framework.scuba.analyses.alias;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.bddbddb.Rel.RelView;
import chord.program.Program;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.SummariesEnv;

public class P2SetComparison {

	protected ProgramRel relVH;
	protected ProgramRel relMV;
	SummaryBasedAnalysis analysis;

	public P2SetComparison(ProgramRel vh, ProgramRel mv,
			SummaryBasedAnalysis sum) {
		relVH = vh;
		relMV = mv;
		analysis = sum;
	}

	// point2set comparison.
	public void run() {
		if (!relVH.isOpen())
			relVH.load();

		if (!relMV.isOpen())
			relMV.load();

		int exact = 0;
		int subSet = 0;
		int superSet = 0;
		int other = 0;
		int chordEmpty = 0;
		int scubaEmpty = 0;
		for (Register r : SummariesEnv.v().getProps()) {
			RelView view = relMV.getView();
			view.selectAndDelete(1, r);
			Iterable<jq_Method> res = view.getAry1ValTuples();
			jq_Method meth = res.iterator().next();
			Set<AllocElem> p2Set = analysis.query(meth.getDeclaringClass(),
					meth, r);
			Set<Quad> sites = new HashSet<Quad>();
			for (AllocElem alloc : p2Set) {
				sites.add(alloc.getSite());
			}

			RelView viewChord = relVH.getView();
			viewChord.selectAndDelete(0, r);
			if (viewChord.size() == 0)
				continue;
			Iterable<Quad> resChord = viewChord.getAry1ValTuples();
			Set<Quad> pts = SetUtils.newSet(viewChord.size());
			// no filter, add all
			for (Quad inst : resChord)
				pts.add(inst);

			System.out.println("P2Set for " + r + " in " + meth);
			System.out.println("[Scuba] " + sites);
			System.out.println("[Chord] " + pts);
			// assert (pts.containsAll(sites));
			if (pts.containsAll(sites) && sites.containsAll(pts)) {
				exact++;
			} else if (pts.containsAll(sites)) {
				subSet++;
			} else if (sites.containsAll(pts)) {
				superSet++;
			} else {
				other++;
			}
			if (pts.isEmpty()) {
				chordEmpty++;
			}
			if (sites.isEmpty()) {
				scubaEmpty++;
			}

			if (sites.isEmpty()) {
				Quad q = pts.iterator().next();

				jq_Type c = null;
				if (q.getOperator() instanceof New) {
					c = (New.getType(q)).getType();
				} else if (q.getOperator() instanceof NewArray) {
					c = (NewArray.getType(q)).getType();
				}
				if (c instanceof jq_Class) {
					if (((jq_Class) c).extendsClass((jq_Class) Program.g()
							.getClass("java.lang.Exception"))
							|| ((jq_Class) c).equals((jq_Class) Program.g()
									.getClass("java.lang.String"))) {
					} else {
						System.out.println("------------------------------");
						System.out.println("Empty happens: ");
						System.out.println("P2Set for " + r + " in " + meth);
						System.out.println("[Scuba] " + sites);
						System.out.println("[Chord] " + pts);
						System.out.println("------------------------------");
					}
				} else {
					System.out.println("------------------------------");
					System.out.println("Empty happens: ");
					System.out.println("P2Set for " + r + " in " + meth);
					System.out.println("[Scuba] " + sites);
					System.out.println("[Chord] " + pts);
					System.out.println("------------------------------");
				}
			}
		}

		System.out
				.println("============================================================");
		System.out.println("[Scuba] [Exhausitive Comparision Statistics]");
		System.out.println("[Scuba] and [Chord] exactly the same: " + exact);
		System.out.println("[Scuba] is better than [Chord]: " + subSet);
		System.out.println("[Scuba] is worse than [Chord]: " + superSet);
		System.out.println("[Scuba] and [Chord] have different results: "
				+ other);
		System.out.println("[Scuba] empty: " + scubaEmpty);
		System.out.println("[Chord] emtpy: " + chordEmpty);
		System.out
				.println("============================================================");
	}
}
