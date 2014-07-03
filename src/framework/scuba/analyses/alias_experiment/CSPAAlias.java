package framework.scuba.analyses.alias_experiment;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.analyses.var.DomV;
import chord.bddbddb.Rel.RelView;
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
    consumes = {"MV", "appsVV", "VValias", "cspaVValias", "cspaVVnotalias",
    		"aliasVVpts", "notaliasVVpts"}
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
	
	
	//for print
	private DomV domV;
	private ProgramRel relaliasVVpts;
	private ProgramRel relnotaliasVVpts;
	

	public void run() {
		domV = (DomV) ClassicProject.g().getTrgt("V");
		
		
		relMV = (ProgramRel)ClassicProject.g().getTrgt("MV");
		
		relappVV = (ProgramRel)ClassicProject.g().getTrgt("appsVV");

		relVV = (ProgramRel) ClassicProject.g().getTrgt("VValias");
		relcspaVValias = (ProgramRel) ClassicProject.g().getTrgt("cspaVValias");
		relcspaVVnotalias = (ProgramRel) ClassicProject.g().getTrgt(
				"cspaVVnotalias");
		
		
		
		//only for print	
		relaliasVVpts = (ProgramRel) ClassicProject.g().getTrgt(
				"aliasVVpts");
		
		relnotaliasVVpts = (ProgramRel) ClassicProject.g().getTrgt(
				"notaliasVVpts");

		
		
		
		print_result();
		
		verbose_print1();
		verbose_print2();
		//verbose_print2();
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

		
		
//		assert (relVV.size() == (relcspaVValias.size() + relcspaVVnotalias
//				.size()));
		
		
		
			
		
		System.out
				.println(" === ==== ==== print CSPA VV  alias begin === ==== ==== ");

		System.out.println("VV alias size  = " + relcspaVValias.size());
		System.out.println("VV not alias size  = " + relcspaVVnotalias.size());
		System.out.println("VV total size = " + relVV.size());
		System.out.println("proved not alias/alias(cipa) :::: "
				+ relcspaVVnotalias.size() + "/" + relVV.size() + "="
				+ (double) relcspaVVnotalias.size()*100 / (double) (relVV.size()) +"%");
	}

	
	
	private void verbose_print2(){
		
		System.out.println("For pairs that may alias ============== ");
		System.out.println("aliasPtsbegin");
		
		if(!relaliasVVpts.isOpen())
			relaliasVVpts.load();
				
		
		RelView view = null;
		
		Iterable<Pair<Register,Register>> vv = relcspaVValias.getAry2ValTuples();

		System.out.println("Total pairs that may alias  ::" + relcspaVValias.size());
		int k = 0;
		for(Pair<Register,Register> ele : vv){
			System.out.println("\n"+ k++ + ": )");
			view = relaliasVVpts.getView();
			
			view.selectAndDelete(0, ele.val0);
			view.selectAndDelete(2, ele.val1);
			
			jq_Method m1 = domV.getMethod(ele.val0);
			jq_Method m2 = domV.getMethod(ele.val1);
			
			
			Iterable<Pair<Ctxt,Ctxt>> ccs = view.getAry2ValTuples();
			
			Set<Ctxt> v1h = new HashSet<Ctxt>();
			Set<Ctxt> v2h = new HashSet<Ctxt>();
			Set<Ctxt> shareh = new HashSet<Ctxt>();
			for(Pair<Ctxt,Ctxt> ele1 :ccs){
					v1h.add(ele1.val0);
					v2h.add(ele1.val1);
			}
			
			for(Ctxt h : v1h){
				if(v2h.contains(h)){
					shareh.add(h);
				}
			}
			
			//make sure that they are alias at some location;
			assert(shareh.size() > 0);
			
			
			System.out.println(ele.val0.toString() + "@" + m1.getDeclaringClass()+"."+m1.getName());
			System.out.println(ele.val1.toString() + "@" + m2.getDeclaringClass()+"."+m2.getName());
			
			
			for(Ctxt sh : shareh){
				System.out.println("Points to same location :: " + sh.head().toString() + "@ context " + sh.toString());
			}
			
			System.out.println("\n Distinct points to information begin ============= ");
			System.out.println(ele.val0.toString()+ " : ");
			for(Ctxt h1 : v1h){
				if(shareh.contains(h1))
					continue;	
				
				System.out.println("--Points to location :: " + h1.head().toString() + "@ context " + h1.toString());
			}
			System.out.println(ele.val1.toString()+ " : ");
			for(Ctxt h2 : v2h){
				if(shareh.contains(h2))
					continue;	
				System.out.println("--Points to location :: " + h2.head().toString() + "@ context " + h2.toString());
			}
		}
		System.out.println("aliasPtsend");	

	}
	
	
	private void verbose_print1(){
			
		System.out.println("NOTAliasPtsBegin");
		
		//notaliasVVpts(v,c,v1,c1)
		if(!relnotaliasVVpts.isOpen())
			relnotaliasVVpts.load();
		
				
		
		RelView view = null;
		
		Iterable<Pair<Register, Register>> vv = relcspaVVnotalias.getAry2ValTuples();
		
		System.out.println("Total pairs that not alias based on not alias vv  ::" + relcspaVVnotalias.size());
		
		
		int k = 0;
		for(Pair<Register,Register> ele : vv){
			System.out.println("\n"+ k++ + ": )");
		
			view = relnotaliasVVpts.getView();
			
			view.selectAndDelete(0, ele.val0);
			view.selectAndDelete(2, ele.val1);
			
			jq_Method m1 = domV.getMethod(ele.val0);
			jq_Method m2 = domV.getMethod(ele.val1);
			
			
			Iterable<Pair<Ctxt,Ctxt>> ccs = view.getAry2ValTuples();
			
			Set<Ctxt> v1h = new HashSet<Ctxt>();
			Set<Ctxt> v2h = new HashSet<Ctxt>();
			Set<Ctxt> shareh = new HashSet<Ctxt>();
			for(Pair<Ctxt,Ctxt> ele1 :ccs){
					v1h.add(ele1.val0);
					v2h.add(ele1.val1);
			}
			
			
			//just for sanity check
			for(Ctxt h : v1h){
				if(v2h.contains(h)){
					shareh.add(h);
				}
			}
			assert(shareh.size() == 0);
			//end of sanity check
			
			
			
						
			System.out.println(ele.val0.toString() + "@" + m1.getDeclaringClass()+"."+m1.getName());
			System.out.println(ele.val1.toString() + "@" + m2.getDeclaringClass()+"."+m2.getName());
					
			System.out.println(ele.val0.toString() + ":");
			for(Ctxt h1 : v1h){				
				System.out.println("Points to location :: " + h1.head().toString() + "@ context " + h1.toString());
			}
			System.out.println(ele.val1.toString()+ ":");
			for(Ctxt h2 : v2h){			
				System.out.println("Points to location :: " + h2.head().toString() + "@ context " + h2.toString());
			}
			
		}
		
		System.out.println("NOTAliasPtsEnd");
	}
}
