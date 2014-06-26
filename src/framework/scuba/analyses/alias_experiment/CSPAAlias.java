package framework.scuba.analyses.alias_experiment;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
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
    name = "cspa-alias-java", 
    consumes = {"MV", "appsVV", "VValias", "cspaVValias", "cspaVVnotalias"}
)
public class CSPAAlias extends JavaAnalysis {

	private ProgramRel relMV;
	private ProgramRel relappVV;
	// total pairs;
	private ProgramRel relVV;

	// alias pairs;
	private ProgramRel relcspaVValias;

	// not alias pairs
	private ProgramRel relcspaVVnotalias;

	public void run() {
		
		relMV = (ProgramRel)ClassicProject.g().getTrgt("MV");
		
		relappVV = (ProgramRel)ClassicProject.g().getTrgt("appsVV");

		relVV = (ProgramRel) ClassicProject.g().getTrgt("VValias");
		relcspaVValias = (ProgramRel) ClassicProject.g().getTrgt("cspaVValias");
		relcspaVVnotalias = (ProgramRel) ClassicProject.g().getTrgt(
				"cspaVVnotalias");

		print_result();
	}

	private void print_result() {
		VV();
	}

	private void VV() {
		System.out.println("========================== cspa-alias-java ========================== ");
		
		
		if(!relMV.isOpen())
			relMV.load();
		
		if(!relappVV.isOpen())
			relappVV.load();
		
		System.out.println("Total pairs that generate by derefrence of variables == " + relappVV.size());
		
		if (!relcspaVValias.isOpen())
			relcspaVValias.load();

		if (!relcspaVVnotalias.isOpen())
			relcspaVVnotalias.load();

		if (!relVV.isOpen())
			relVV.load();

		
		
		assert (relVV.size() == (relcspaVValias.size() + relcspaVVnotalias
				.size()));
		
		//Iterable<Pair<Register,Register>> appsvv = relappVV.getAry2ValTuples();
		Iterable<Pair<Register, Register>> vv = relVV.getAry2ValTuples();
		Iterable<Pair<jq_Method, Register>> mv = relMV.getAry2ValTuples();
		
		
		Map<Register, jq_Method> vmmap = new HashMap<Register, jq_Method>();
		
		
		for(Pair<jq_Method, Register> ele : mv){
			vmmap.put(ele.val1, ele.val0);
		}
		
		System.out.println("--------------- check total alias pairs in CIPA begin !-------------------");
		System.out.println("CIPA ALIAS Total size is " + relVV.size());
		int k = 0;
		for(Pair<Register, Register> ele : vv){
			Register v1 = ele.val0;
			Register v2 = ele.val1;
			
			jq_Method m1 = vmmap.get(v1);
			
			jq_Method m2 = vmmap.get(v2);
			
			System.out.print(k++ + ") ");
			System.out.print(m1.toString() + " :: " + v1.toString());
			System.out.println(" && " + m2.toString() + " :: " + v2.toString());
			
			//System.out.println(ele.toString());
			
		}
		System.out.println("--------------- check total alias pairs in CIPA end !----------------------");
		
		System.out
				.println(" === ==== ==== print CSPA VV  alias begin === ==== ==== ");

		System.out.println("VV alias size  = " + relcspaVValias.size());
		System.out.println("VV not alias size  = " + relcspaVVnotalias.size());
		System.out.println("VV total size = " + relVV.size());
		System.out.println("proved not alias/alias(cipa) :::: "
				+ relcspaVVnotalias.size() + "/" + relVV.size() + "="
				+ (double) relcspaVVnotalias.size()*100 / (double) (relVV.size()) +"%");
	}

}
