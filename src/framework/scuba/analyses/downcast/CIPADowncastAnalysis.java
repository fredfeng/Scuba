package framework.scuba.analyses.downcast;

import joeq.Class.jq_Method;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

@Chord(
        name = "cipa-downcast-java",
        consumes = {"cipaunsafeDowncast", "cipasafeDowncast", "cipadowncast", 
        		/*"MobjVarAsgnInst", "McheckCastInst",*/"reachableM", "reachableappsM"}
)
public class CIPADowncastAnalysis extends JavaAnalysis {
		private ProgramRel relreachableM;
		private ProgramRel relreachableappsM;
		
		
		private ProgramRel reldowncast;
		//private ProgramRel relMobjVarAsgnInst;
		//private ProgramRel relMcheckCastInst;
		private ProgramRel relunsafeDowncast;
		private ProgramRel relsafeDowncasst;
		
        public void run() {
              
                ClassicProject.g().runTask("cipa-downcast-dlog");
                
                //relMobjVarAsgnInst = (ProgramRel) ClassicProject.g().getTrgt("MobjVarAsgnInst");
                
                relreachableM =  (ProgramRel) ClassicProject.g().getTrgt("reachableM");
                
                relreachableappsM = (ProgramRel)ClassicProject.g().getTrgt("reachableappsM");

                
                //relMcheckCastInst = (ProgramRel) ClassicProject.g().getTrgt("McheckCastInst");
                
                reldowncast = (ProgramRel) ClassicProject.g().getTrgt("cipadowncast");
                
                relunsafeDowncast = (ProgramRel) ClassicProject.g().getTrgt("cipaunsafeDowncast");
                relsafeDowncasst = (ProgramRel) ClassicProject.g().getTrgt("cipasafeDowncast");
                
//                if(!relMobjVarAsgnInst.isOpen())
//                	relMobjVarAsgnInst.load();
                
               if(!relreachableM.isOpen())
            	   relreachableM.load();
               
               
               if(!relreachableappsM.isOpen())
               	relreachableappsM.load();

//                if(!relMcheckCastInst.isOpen())
//                	relMcheckCastInst.load();
                
                
               
                if(!reldowncast.isOpen())
                	reldowncast.load();
                
                if(!relunsafeDowncast.isOpen())
                	relunsafeDowncast.load();
                
                if(!relsafeDowncasst.isOpen())
                	relsafeDowncasst.load();
                
                TestingAppMReachable();
                
                print_result();
        }
        
        private void TestingAppMReachable(){
        	
        	Iterable<jq_Method> ms = relreachableappsM.getAry1ValTuples();
        	
        	
        	System.out.println("============ Starting test exclude libraries method ============= ");
        	int i = 0;
        	for(jq_Method m : ms){
        		System.out.println(i++ + " : ReachableAppm  == " + m.toString());
        	}
        	System.out.println("============ End testing exclude libraries method ============= ");
        }
        
        private void print_result(){
            int size_of_unsafety = relunsafeDowncast.size();
            int size_of_safety = relsafeDowncasst.size();

            System.out.println("Total Reachable Method ::" + relreachableM.size());
            System.out.println("Total Reachable Application Method :: " + relreachableappsM.size());
            //System.out.println("Total checkcast instruction ::" + relMcheckCastInst.size());  
            //System.out.println("Total objVarAssignment " + relMobjVarAsgnInst.size());
            System.out.println("Total downcast::" + reldowncast.size());
        	System.out.println("nosafe  !! " + size_of_unsafety);
        	System.out.println("safe  !! " + size_of_safety);
        	System.out.println("Percentage unsafety ==> " 
        	+ (float)size_of_unsafety/(float)(size_of_unsafety+size_of_safety));
        	
        }
}

