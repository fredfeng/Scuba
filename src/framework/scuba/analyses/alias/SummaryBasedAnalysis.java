/**
 * Summary-based analysis.
 * author: Yu Feng
 * email: yufeng@cs.utexas.edu
 */
package framework.scuba.analyses.alias;

import java.util.LinkedList;


public class SummaryBasedAnalysis {
	
	public SummaryBasedAnalysis() {
		//init \gamma
		//compute SCCs and their representative nodes.
	}
	
	
	public void sumAnalyze() {
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
	
	public void analyze() {
		//do interproc
		//mark terminated
	}
	
	public void analyzeSCC() {
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
}
