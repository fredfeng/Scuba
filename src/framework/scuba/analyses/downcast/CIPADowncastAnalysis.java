package framework.scuba.analyses.downcast;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

@Chord(
        name = "cipa-downcast-java",
        consumes = {"cipaunsafeDowncast", "cipasafeDowncast", "cipadowncast", 
        		"MobjVarAsgnInst", "McheckCastInst","reachableM"}
)
public class CIPADowncastAnalysis extends JavaAnalysis {
		private ProgramRel relreachableM;

		private ProgramRel reldowncast;
		private ProgramRel relMobjVarAsgnInst;
		private ProgramRel relMcheckCastInst;
		private ProgramRel relunsafeDowncast;
		private ProgramRel relsafeDowncasst;
        public void run() {
        	
                ClassicProject.g().runTask("cipa-downcast-dlog");
                
                relMobjVarAsgnInst = (ProgramRel) ClassicProject.g().getTrgt("MobjVarAsgnInst");
                
                relreachableM =  (ProgramRel) ClassicProject.g().getTrgt("reachableM");

                
                relMcheckCastInst = (ProgramRel) ClassicProject.g().getTrgt("McheckCastInst");
                
                reldowncast = (ProgramRel) ClassicProject.g().getTrgt("cipadowncast");
                
                relunsafeDowncast = (ProgramRel) ClassicProject.g().getTrgt("cipaunsafeDowncast");
                relsafeDowncasst = (ProgramRel) ClassicProject.g().getTrgt("cipasafeDowncast");
                
                if(!relMobjVarAsgnInst.isOpen())
                	relMobjVarAsgnInst.load();
                
               if(!relreachableM.isOpen())
            	   relreachableM.load();

                if(!relMcheckCastInst.isOpen())
                	relMcheckCastInst.load();
                
                
               
                if(!reldowncast.isOpen())
                	reldowncast.load();
                
                if(!relunsafeDowncast.isOpen())
                	relunsafeDowncast.load();
                
                if(!relsafeDowncasst.isOpen())
                	relsafeDowncasst.load();
                
                print_result();
        }
        
        private void print_result(){
            int size_of_unsafety = relunsafeDowncast.size();
            int size_of_safety = relsafeDowncasst.size();

            System.out.println("Total Reachable Method ::" + relreachableM.size());
            System.out.println("Total checkcast instruction ::" + relMcheckCastInst.size());  
            System.out.println("Total objVarAssignment " + relMobjVarAsgnInst.size());
            System.out.println("Total downcast::" + reldowncast.size());
        	System.out.println("nosafe  !! " + size_of_unsafety);
        	System.out.println("safe  !! " + size_of_safety);
        	System.out.println("Percentage unsafety ==> " 
        	+ (float)size_of_unsafety/(float)(size_of_unsafety+size_of_safety));
        	
        }
}

