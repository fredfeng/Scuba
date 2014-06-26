package framework.scuba.analyses.librariesfilter;
import framework.scuba.utils.ChordUtil;
import joeq.Class.jq_Class;
import chord.program.visitors.IClassVisitor;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.Utils;




/**
 * Relation containing each type t the prefix of whose name is not contained in the value specified by classes and excludeAry
 *
 * @author haiyanzhu
 */
@Chord(
    name = "librariesT",
    sign = "T0:T0"
)
public class RelLibrariesT extends ProgramRel implements IClassVisitor {

	public final static String[] ExcludeStdLibs = {
		"java.", "javax.", "sun.",
		"com.sun.", "com.ibm.", "org.apache.harmony."
	};
	
	public final static String[] ExcludeAryForShare = {"([a-zA-Z])+",  //dacapo-9.12 + 
		"org.apache.commons.cli.", "org.dacapo.harness.", "org.dacapo.parser.",  //dacapo-9.12
		"dacapo.parser.", "dacapo\\.([a-zA-Z]|\\$[0-9])+" //dacapo-2006-10-MR2
	};
	
	public final static String[] ExcludeAry = {
		"javax.xml.parsers.", "org.apache.crimson.", "org\\.w3c\\.dom\\.([a-zA-Z])+", "org.xml.sax.", 
		"org.w3c.css.sac", "org.w3c.dom.smil.","org.w3c.dom.svg", //filter for batik
		"com.lowagie.", //filter for chart
		"org.apache.tools.", "org.objectweb.asm.signature.", 
		"org.jaxen.", "junit.", "org.hamcrest.", "org.junit.", "org.objectweb.",//filter for pmd
		"org.codehaus.janino.",//filter for sunflow
		"org.apache.xml.serializer."//filter for xalan
		}; 
	
	//this is used to filter project fop, which takes advantage of another benchmark batik's jar, processed separately  
	public final static String[] fopExcludeAry = {"org.apache.avalon.framework.", //avalon-framework-4.2.0.jar
		"org.apache.batik.", "org.w3c.dom.events.", // batik-all-1.7.jar
		"org.apache.commons.io", //commons-io-1.3.1.jar
		"org.apache.commons.logging.",//commons-logging-1.0.4.jar
		"org.w3c.css.sac.helpers.", "org.w3c.css.sac.helpers.","org.w3c.dom.smil.", "org.w3c.dom.svg.", //xml-apis-ext.jar
		"org.apache.xmlgraphics.", //xmlgraphics-commons-1.3.1.jar
		"org.xml.sax."
		
	};
	
    public void visit(jq_Class c) {
    	
    	 if(Config.userClassPathName.contains("fop")){
    		 //System.out.println("Filter for fop !!");
         	if(ChordUtil.prefixMatch(c.getName(), fopExcludeAry)){
//         		System.out.println("Exclude for fop's included jar ... " + c.getName());
         		add(c);
         	}else if(ChordUtil.prefixMatch(c.getName(), ExcludeAryForShare)){
//         		System.out.println("Exclude for share... " + c.getName());
         		add(c);
         	}else if(ChordUtil.prefixMatch(c.getName(), ExcludeStdLibs)){
//         		System.out.println("Exclude for standard libaries " + c.getName());
         		add(c);
         	}
		} else {
			//System.out.println("Filter not for fop !!");
			if (ChordUtil.prefixMatch(c.getName(), ExcludeAry)){
//				System.out.println("Exclude for included jar ... " + c.getName());
				add(c);
			}else if(ChordUtil.prefixMatch(c.getName(), ExcludeAryForShare)){
//				System.out.println("Exclude for Share... " + c.getName());
				add(c);
			}else if(ChordUtil.prefixMatch(c.getName(), ExcludeStdLibs)){
//         		System.out.println("Exclude for standard libaries " + c.getName());
         		add(c);
         	}
		}
        
       
    }
}
