package framework.scuba.analyses.alias;

import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import chord.analyses.alias.CICG;
import chord.analyses.alias.ICICG;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

/**
 * Summary-based analysis.
 * 1. Build and get a CHA-based CallGraph.
 * 2. Compute SCC
 * 3. Run the worklist algorithm 
 * author: Yu Feng
 * email: yufeng@cs.utexas.edu
 */

@Chord(
	    name = "sum-java",
	    consumes = { "rootM", "reachableM", "IM", "MM" }
	)
public class SummaryBasedAnalysis extends JavaAnalysis {
	

    protected DomM domM;
    protected ProgramRel relRootM;
    protected ProgramRel relReachableM;
    protected ProgramRel relIM;
    protected ProgramRel relMM;
    protected CICG callGraph;
    
	private void init() {
		getCallGraph();
		System.out.println("Total nodes in CG---------" + callGraph.getNodes().size());
		//init \gamma here.
		//compute SCCs and their representative nodes.
		sumAnalyze();
	}
	
	
	private void sumAnalyze() {
		Set<jq_Method> roots = callGraph.getRoots();
		for(Set<jq_Method> scc : callGraph.getTopSortedSCCs()) {
			System.out.println("SCC List---" + scc);
		}
		//foreach leaf in the callgraph. Add them to the worklist.
		/*LinkedList worklist = new LinkedList();
		while(!worklist.isEmpty()) {
			worker = worklist.poll();
			if(success terminated) {
				analyze(worker);
			} else
				append worker to the end of the List.class
				
		    add m's pred to worklist
		    
		    
		}*/
	}
	
	private void analyze() {
		//do interproc
		//mark terminated
	}
	
	private void analyzeSCC() {
		//add all methods to worklist
/*		while(wl is not empty) {
			gamma = worker.getSummary();
			m = worklist.poll();
			analyze(m);
			reset
			gammaNew = m.getSummary();
			if(gammaNew == gamma)
				set
			else
				reset
			add pred(unterminated)
		}*/
	}
	
	/*This method will be invoked by Chord automatically.*/
    public void run() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        relRootM = (ProgramRel) ClassicProject.g().getTrgt("rootM");
        relReachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
        relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
        relMM = (ProgramRel) ClassicProject.g().getTrgt("MM");
        //init scuba.
        init();
    }
    /**
     * Provides the program's context-insensitive call graph.
     * 
     * @return The program's context-insensitive call graph.
     */
    public ICICG getCallGraph() {
        if (callGraph == null) {
            callGraph = new CICG(domM, relRootM, relReachableM, relIM, relMM);
        }
        return callGraph;
    }
    /**
     * Frees relations used by this program analysis if they are in memory.
     * <p>
     * This method must be called after clients are done exercising the interface of this analysis.
     */
    public void free() {
        if (callGraph != null)
            callGraph.free();
        
    }

}
