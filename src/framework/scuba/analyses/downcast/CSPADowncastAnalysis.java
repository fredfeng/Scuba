/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package framework.scuba.analyses.downcast;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.CtxtsAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Static downcast safety analysis.
 * <p> 
 * Outputs relations <tt>safeDowncast</tt> and <tt>unsafeDowncast</tt>
 * containing pairs (v,t) such that local variable v of reference type
 * (say) t' may be cast to reference type t which is not a supertype
 * of t', and the cast, as deemed by this analysis, is either provably
 * safe or possibly unsafe, respectively.
 * <p>
 * Recognized system properties:
 * <ul>
 * <li>All system properties recognized by abstract contexts analysis
 * (see {@link chord.analyses.alias.CtxtsAnalysis}).</li>
 * </ul>
 *
 *
 */
@Chord(
	name = "cspa-downcast-java",
	consumes = {"reachableCM","reachableM", "downcast", "unsafeDowncast", "safeDowncast", "reachableappsCM"}
)
public class CSPADowncastAnalysis extends JavaAnalysis {
		private ProgramRel relreachableM;
		private ProgramRel relreachableCM;
		private ProgramRel reldowncast;
		
		private ProgramRel relreachableappsCM;

		private ProgramRel relunsafeDowncast;
		private ProgramRel relsafeDowncasst;
		
//		private ProgramRel relMcheckCastInst;
//		
//		private ProgramRel relMobjVarAsgnInst;
		
		

	public void run() {
		System.out.println("Running Ctxts task is  "
				+ CtxtsAnalysis.getCspaKind().toString());
		ClassicProject.g().runTask(CtxtsAnalysis.getCspaKind());

		ClassicProject.g().runTask("cspa-downcast-dlog");

		relreachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");

		relreachableCM = (ProgramRel) ClassicProject.g().getTrgt("reachableCM");
		
		relreachableappsCM = (ProgramRel)ClassicProject.g().getTrgt("reachableappsCM");
		
		
//		relMcheckCastInst = (ProgramRel)ClassicProject.g().getTrgt("McheckCastInst");
//		
//		relMobjVarAsgnInst = (ProgramRel)ClassicProject.g().getTrgt("MobjVarAsgnInst");

		reldowncast = (ProgramRel) ClassicProject.g().getTrgt("downcast");

		relunsafeDowncast = (ProgramRel) ClassicProject.g().getTrgt(
				"unsafeDowncast");
		relsafeDowncasst = (ProgramRel) ClassicProject.g().getTrgt(
				"safeDowncast");

		if (!relreachableM.isOpen())
			relreachableM.load();

		if (!relreachableCM.isOpen())
			relreachableCM.load();
		
		
		if(!relreachableappsCM.isOpen())
			relreachableappsCM.load();

		if (!reldowncast.isOpen())
			reldowncast.load();

		if (!relunsafeDowncast.isOpen())
			relunsafeDowncast.load();

		if (!relsafeDowncasst.isOpen())
			relsafeDowncasst.load();
		
//		if(!relMcheckCastInst.isOpen())
//			relMcheckCastInst.load();
//		
//		if(!relMobjVarAsgnInst.isOpen())
//			relMobjVarAsgnInst.load();

		print_result();
	}

	private void print_result() {
		int size_of_unsafety = relunsafeDowncast.size();
		int size_of_safety = relsafeDowncasst.size();

		// sanity check
		Set<jq_Method> ms = new HashSet<jq_Method>();
		System.out
				.println("Total Reachable CMethod ::" + relreachableCM.size());
		Iterable<Pair<Ctxt, jq_Method>> cms = relreachableCM.getAry2ValTuples();
		for (Pair<Ctxt, jq_Method> cm : cms) {
			ms.add(cm.val1);
		}

		System.out.println("Total Reachable Method ::" + relreachableM.size());
		System.out.println("Total Reachable Methods inside CM :: " + ms.size());
		
		System.out.println("Total Reachable AppsM inside CM :: " + relreachableappsCM.size());
		
		
//		System.out.println("Total checkcast instruction ::" + relMcheckCastInst.size());
//        
//        
//        
//        System.out.println("Total objVarAssignment " + relMobjVarAsgnInst.size());
//		
		

		assert ((size_of_unsafety + size_of_safety) == reldowncast.size());

		System.out.println("Total downcast ::" + reldowncast.size());
		System.out.println("nosafe  !! " + size_of_unsafety);
		System.out.println("safe  !! " + size_of_safety);
		System.out.println("Percentage unsafety " + (float) size_of_unsafety
				/ (float) reldowncast.size());

	}


		
}
