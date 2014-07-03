package framework.scuba.analyses.alias_experiment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * refine appsVV because of duplicated this pointer
 *
 * @author haiyan
 */
@Chord(name = "vv-refine-java",
consumes = { "V", "appsVV" },
produces = { "VVRefined" },
namesOfTypes = { "V" },
types = { DomV.class }
)
public class VVRefine extends JavaAnalysis {
	private DomV domV;
	private ProgramRel relVV;
	
	private ProgramRel relVVRefined;
	
    public void run() {
    	
    	domV = (DomV) ClassicProject.g().getTrgt("V");
    	relVV =(ProgramRel)ClassicProject.g().getTrgt("appsVV");    
    	relVVRefined= (ProgramRel)ClassicProject.g().getTrgt("VVRefined");
    	
    	refineVV();
    }
    
    
	private void refineVV() {
		relVVRefined.zero();

		if (!relVV.isOpen())
			relVV.load();

		Iterable<Pair<Register, Register>> vv = relVV.getAry2ValTuples();
		
		Map<jq_Class,Set<Register>> refine_map = new HashMap<jq_Class, Set<Register>>();
		
		Set<Pair<jq_Class, jq_Class> >  class_to_class_set  = new HashSet<Pair<jq_Class, jq_Class>>();

		for (Pair<Register, Register> ele : vv) {
			Register r1 = ele.val0;
			Register r2 = ele.val1;

			jq_Method m1 = domV.getMethod(r1);
			jq_Class c1 = m1.getDeclaringClass();

			jq_Method m2 = domV.getMethod(r2);
			jq_Class c2 = m2.getDeclaringClass();

			//r1 = R0 && r2= R0;
			if (r1.toString().equals("R0") && r2.toString().equals("R0")){
				if(c1.equals(c2)){
					continue;
				}else{
					Pair<jq_Class, jq_Class> ccp1 = new Pair<jq_Class, jq_Class>(c1, c2);
										
					if( class_to_class_set.contains(ccp1)){
						continue;
					}else{
						relVVRefined.add(r1,r2);
						 class_to_class_set.add(ccp1);
					}
				}
			}
			
			
			//r1 = R0 && r2 != R0
			if(r1.toString().equals("R0")&& !r2.toString().equals("R0")){
				if(refine_map.containsKey(c1)){
					if(refine_map.get(c1).contains(r2)){
						continue;
					}else{
						relVVRefined.add(r1, r2);
						refine_map.get(c1).add(r2);
					}
					
				}else{
					relVVRefined.add(r1, r2);					
					Set<Register> rs = new HashSet<Register>();
					rs.add(r2);
					refine_map.put(c1, rs);
				}
			}
			
			//r2 = R0 && r1 != R0
			if(r2.toString().equals("R0")&& !r1.toString().equals("R0")){
				if(refine_map.containsKey(c2)){
					if(refine_map.get(c2).contains(r1)){
						continue;
					}else{
						relVVRefined.add(r1, r2);
						refine_map.get(c2).add(r1);
					}
					
				}else{
						relVVRefined.add(r1, r2);		
						Set<Register> rs = new HashSet<Register>();
						rs.add(r1);
						refine_map.put(c2, rs);
				}
			}		
		}

		relVVRefined.save();
	}

}
