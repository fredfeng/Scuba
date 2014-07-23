package framework.scuba.analyses.downcast;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.bddbddb.Rel.RelView;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Trio;
import framework.scuba.analyses.alias.SummaryBasedAnalysis;
import framework.scuba.domain.AllocElem;
import framework.scuba.utils.StringUtil;

/**
 * Checking downcast safety based on points-to information Our percentage of
 * unsafety cast should be less than Chord
 */
public class DowncastAnalysis {

	protected ProgramRel relDcm;
	protected ProgramRel relDVH;
	SummaryBasedAnalysis analysis;

	public DowncastAnalysis(ProgramRel dcm, ProgramRel dvh,
			SummaryBasedAnalysis sum) {
		relDcm = dcm;
		relDVH = dvh;
		analysis = sum;
	}

	// downcast analysis.
	public void run() {
		if (!relDcm.isOpen())
			relDcm.load();

		if (!relDVH.isOpen())
			relDVH.load();

		RelView view = relDcm.getView();
		Iterable<Trio<jq_Method, Register, jq_Type>> res = view
				.getAry3ValTuples();

		StringUtil.reportInfo("Number of downcast: " + relDcm.size());
		int succScuba = 0;
		int succChord = 0;
		int empScuba = 0;
		int empChord = 0;

		for (Trio<jq_Method, Register, jq_Type> trio : res) {
			jq_Method meth = trio.val0;
			Register r = trio.val1;
			jq_Type castType = trio.val2;
			// System.out.println(meth + " reg: " + r + " Type: " + trio.val2);
			Set<AllocElem> p2Set = analysis.query(meth.getDeclaringClass(),
					meth, r);

			boolean dcScuba = true;
			Set<Quad> sites = new HashSet<Quad>();
			if (p2Set.isEmpty())
				empScuba++;

			for (AllocElem alloc : p2Set) {
				sites.add(alloc.getSite());
				if (castType.isArrayType()) {
					if (!alloc.getType().isArrayType())
						dcScuba = false;
				} else {
					if (alloc.getType().isArrayType())
						dcScuba = false;
					else {
						jq_Class castClz = (jq_Class) castType;
						jq_Class allocClz = (jq_Class) alloc.getType();
						if (!allocClz.extendsClass(castClz)
								&& !allocClz.implementsInterface(castClz))
							dcScuba = false;
					}
				}
			}

			boolean dcChord = true;
			// p2set of r in chord.
			RelView viewChord = relDVH.getView();
			viewChord.selectAndDelete(0, r);
			Iterable<Quad> resChord = viewChord.getAry1ValTuples();
			Set<Quad> pts = SetUtils.newSet(viewChord.size());
			// no filter, add all
			for (Quad inst : resChord) {
				pts.add(inst);

				if (!(inst.getOperator() instanceof New))
					dcChord = false;
				else {
					TypeOperand to = New.getType(inst);
					jq_Type t = to.getType();
					if (castType.isArrayType()) {
						if (!t.isArrayType())
							dcChord = false;
					} else {
						jq_Class castClz = (jq_Class) castType;
						if (t.isArrayType())
							dcChord = false;
						else {
							jq_Class allocClz = (jq_Class) t;
							// nice.
							if (!allocClz.extendsClass(castClz)
									&& !allocClz.implementsInterface(castClz))
								dcChord = false;
						}
					}
				}
			}
			if (pts.size() == 0)
				empChord++;

			StringUtil.reportInfo("[Scuba] method: " + meth);
			StringUtil.reportInfo("[Scuba] Downcast Type: " + castType);
			StringUtil.reportInfo("[Scuba] p2Set of " + r + ": " + sites);
			StringUtil.reportInfo("[Scuba] cast result: " + dcScuba);
			StringUtil.reportInfo("[Chord] cast result: " + dcChord);
			StringUtil.reportInfo("[Chord] p2Set of " + r + ": " + pts);
			if (dcChord)
				succChord++;
			if (dcScuba)
				succScuba++;
		}

		StringUtil.reportInfo("[Scuba] final: " + succScuba);
		StringUtil.reportInfo("[Chord] final: " + succChord);
		StringUtil.reportInfo("[Scuba] empty: " + empScuba);
		StringUtil.reportInfo("[Chord] empty: " + empChord);

	}
}
