package framework.scuba.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.BytecodeToQuad.jq_ReturnAddressType;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getfield.GETFIELD_A;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.CIPAAnalysis;
import chord.bddbddb.Rel.RelView;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;
import framework.scuba.analyses.librariesfilter.RelLibrariesT;

public class ChordUtil {
	
	//either a is the subclass of b or b is the subclass of a.
	//or one implement other's interface.
	public static boolean checkCompatible(jq_Class a, jq_Class b) {
		return a.implementsInterface(b) || b.implementsInterface(a)
				|| a.extendsClass(b) || b.extendsClass(a);
	}

	public static boolean prefixMatch(String str, String[] prefixes) {
        for (String prefix : prefixes) {
            if (str.startsWith(prefix))
                return true;
            
            //haiyan added regular expression matching
            if(str.matches(prefix))
            	return true;
        }
        return false;
	}
	//is this a library method?
	public static boolean isLibClass(jq_Class clz) {
		String str = clz.getName();
		
        for (String prefix : RelLibrariesT.ExcludeStdLibs) {
            if (str.startsWith(prefix))
                return true;
            
            //haiyan added regular expression matching
            if(str.matches(prefix))
            	return true;
        }
        return false;
	}
	
	//Does this method have load stmt of a given field?
	public static boolean hasLoadInst(jq_Method meth, jq_Field field) {
		ControlFlowGraph cfg = meth.getCFG();
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (!(op instanceof GETFIELD_A))
					continue;
				jq_Field src = Getfield.getField(q).getField();
				if(field.equals(src))
					return true;
			}
		}
		return false;
	}
	
	static Set<Register> vhRegs = new HashSet<Register>();
	
	//whether f can reach any return value or arguments.
	public static boolean isEscapeFromMeth(CIPAAnalysis cipa, jq_Method meth,
			jq_Field field) {
		//1.collect all registers that related to either return value or args.
		Set<Register> sumRegs = new HashSet<Register>();
		Set<CIObj> reachablePts = new HashSet<CIObj>();
		ControlFlowGraph cfg = meth.getCFG();
		
		RegisterFactory rf = cfg.getRegisterFactory();
		//r0: this pointer.
		Register r0 = rf.get(0);
		System.out.println("checking method: " + meth + " field: " + field);
		//whether r0 is empty?
		if(vhRegs.size() == 0) {
			ProgramRel relVH  = (ProgramRel) ClassicProject.g().getTrgt("VH");
	        relVH.load();
	        RelView view = relVH.getView();
	        view.delete(1);
	        Iterable<Register> res = view.getAry1ValTuples();
			for (Register vv : res)
				vhRegs.add(vv);
		}

		if(!vhRegs.contains(r0)) {
			System.out.println("pterror:" + r0 + " meth: " + meth + ":vh:" +  vhRegs.size());
			return false;
		}
		
		CIObj r0Pts = cipa.pointsTo(r0);
		
		//points-to set of r0.f?
		CIObj rootPts = cipa.pointsTo(r0Pts, field);
		if(rootPts.pts.isEmpty())
			return false;
		
		reachablePts.add(rootPts);
		//Collect all the reachable pt objs from root.
		Set<jq_Field> drvFields = getAllLoadFields(meth);
		for(jq_Field f : drvFields) {
			//we already consider this.
			if(f.equals(field))
				continue;
			CIObj newSet = null;
			for(CIObj pt : reachablePts){
				CIObj subPts = cipa.pointsTo(pt, f);
				if(subPts.pts.isEmpty())
					continue;
				newSet = subPts;
			}
			if(newSet != null)
				reachablePts.add(newSet);
		}
		
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				//arguments. shall we include r0, the this pointer?
				if (op instanceof Invoke) {
					ParamListOperand l = Invoke.getParamList(q);
					int numArgs = l.length();
					for (int zIdx = 0; zIdx < numArgs; zIdx++) {
						RegisterOperand vo = l.get(zIdx);
						if (vo.getType().isAddressType()
								|| vo.getType() instanceof jq_NullType)
							continue;
						Register v = vo.getRegister();
						if (v.getType().isReferenceType())
							sumRegs.add(v);
					}
				}
				//return values.
				if (op instanceof RETURN_A) {
					Operand operand = Return.getSrc(q);
					if (!(operand instanceof RegisterOperand))
						continue;

					RegisterOperand ro = (RegisterOperand) operand;
					jq_Type retType = ro.getType();
					if (retType instanceof jq_NullType
							|| retType instanceof jq_ReturnAddressType)
						continue;
					sumRegs.add(ro.getRegister());
				}
			}
		}
		
		//field is reachable from r?
		for (Register r : sumRegs) {
			CIObj src = cipa.pointsTo(r);
			for (CIObj tgt : reachablePts)
				if (src.mayAlias(tgt))
					return true;
		}
		return false;
	}
	
	//return all fields related to load stmts.
	public static Set<jq_Field> getAllLoadFields(jq_Method meth) {
		Set<jq_Field> set = new HashSet<jq_Field>();
		ControlFlowGraph cfg = meth.getCFG();
		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (!(op instanceof GETFIELD_A))
					continue;
				jq_Field src = Getfield.getField(q).getField();
				set.add(src);
			}
		}
		return set;
	}
}
