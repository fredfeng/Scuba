package chord.analyses.alias_experiment;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.DomC;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (m,i) such that method m contains
 * method invocation quad i.
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
    	relVVRefined= (ProgramRel)ClassicProject.g().getTrgt("VVRefine");
    	
    	refineVV();
    }
    
    
    private void refineVV(){
    	relVVRefined.zero();
    	
    	if(!relVV.isOpen())
    		relVV.load();
    	
    	Iterable<Pair<Register,Register>> vv = relVV.getAry2ValTuples();
    	
    	
    	for(Pair<Register, Register> ele : vv){
    		Register r1 = ele.val0;
    		Register r2 = ele.val0;
    		
    		if(r1.toString().equals("R0"))
    			continue;
    		
    		if(r2.toString().equals("R0"))
    			continue;
    		
    		
    		jq_Method m1 = domV.getMethod(r1);
    		jq_Class c1 = m1.getDeclaringClass();
    		
    		jq_Method m2 = domV.getMethod(r2);
    		jq_Class c2 = m2.getDeclaringClass();
    		
    		if(!c1.equals(c2)){
    			System.out.println("Keep:::" + r1.toString()+ ":: " + m1.toString() + "@" + c1.toString() );
    			System.out.println(r2.toString()+ ":: " + m2.toString() + "@" + c2.toString() );
    			relVVRefined.add(r1,r2);
    		}else{
    			System.out.println("filter out::: " + r1.toString()+ ":: " + m1.toString() + "@" + c1.toString() );
    			System.out.println(r2.toString()+ ":: " + m2.toString() + "@" + c2.toString() );
    		}
    		
    		
    	}
    	
    	relVVRefined.save();
    }
}
