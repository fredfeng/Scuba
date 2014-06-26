package chord.analyses.virtual_call_resolve_experiment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;


/**
 * 
 * 
 * @author haiyan zhu 
 */
@Chord(
    name = "cipa-vc-resolve-java", 
    consumes = {"cipavirtualIM"}
)
public class CIPAVCResolve extends JavaAnalysis {
	
	
	
	
	// alias pairs;
    private ProgramRel relvirtualIM;
    
    private Map<Quad, Set<jq_Method>> ims;
    
    private Set<Quad> single_target_virtualc;
  
    
    int max_target;
    
    Quad max_targets_invoke;
    
    int total_targets;
    
   
    
    public void run() {
    	
    	
    	relvirtualIM = (ProgramRel)ClassicProject.g().getTrgt("cipavirtualIM");
    	ims = new HashMap<Quad, Set<jq_Method>>();
    	
    	single_target_virtualc = new HashSet<Quad>();
    	
    	max_target = 0;
    	
    	total_targets  = 0;    	
    	
    	print_result();  	
    }
    
    private void print_result(){
    	System.out.println("================== cipa-vc-resolve-java ============================");
    	if(!relvirtualIM.isOpen())
    		relvirtualIM.load();
    	
    	
    	Iterable<Pair<Quad,jq_Method>> im = relvirtualIM.getAry2ValTuples();
    	
    	
    	for( Pair<Quad, jq_Method> ele : im){	
    		if(ims.containsKey(ele.val0)){
    			ims.get(ele.val0).add(ele.val1);
    		}else{
    			Set<jq_Method> targets = new HashSet<jq_Method>();
    			
    			targets.add(ele.val1);
    			
    			ims.put(ele.val0, targets);
    		}
    	}
    	
    	
    	
    	int target_size = 0;
    	for (Quad q : ims.keySet()) {
    		target_size = ims.get(q).size();
    		
    		
    		total_targets += target_size;
    		
    		if(target_size == 1){
    			assert(!single_target_virtualc.contains(q));
    			single_target_virtualc.add(q);
    		}
    		
    		if(target_size > max_target){
    			max_target = target_size;
    			max_targets_invoke = q;
    		}
    	}
    	
    	
    	System.out.println("Single target size :: " + single_target_virtualc.size());
    	
//    	System.out.println("----------------- single target verbose begin ............ ");
//    	int idx = 0;
//    	for(Quad  q : single_target_virtualc){
//    		System.out.println(idx++ + ") :: single target invoking is " + q.toString());
//    		for(jq_Method m : ims.get(q)){
//    			System.out.println(" target is " + m.toString());
//    		}
//    	}
//    	System.out.println("------------------- single target verbose end ............ ");
    	
    	System.out.println("Max target size  :: " + max_target);
    	
//    	int k = 0;
//    	System.out.println("Max Invoking is  == " + max_targets_invoke.toString());
//    	for(jq_Method t : ims.get(max_targets_invoke)){
//    		System.out.println(k++ + "):: " + t.toString());
//    	}
    	
    	System.out.println("Polymorphic callsites " + (ims.size() - single_target_virtualc.size()));
    	
    	System.out.println("Average target size :: " + total_targets + "/" + ims.size() + "="+ (double)total_targets/(double) ims.size());
    	
    	
    }
    
}