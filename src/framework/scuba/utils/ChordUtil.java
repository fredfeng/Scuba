package framework.scuba.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Status;

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
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Putstatic.PUTSTATIC_A;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.field.DomF;
import chord.bddbddb.Rel.RelView;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.tuple.object.Pair;
import framework.scuba.analyses.librariesfilter.RelLibrariesT;
import framework.scuba.domain.Env;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.helper.G;

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
        
        for (String prefix : RelLibrariesT.ExcludeAryForShare) {
            if (str.startsWith(prefix))
                return true;
            
            //haiyan added regular expression matching
            if(str.matches(prefix))
            	return true;
        }
        
        for (String prefix : RelLibrariesT.fopExcludeAry) {
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
		Set<CIObj> sumRegs = new HashSet<CIObj>();
		Set<CIObj> reachablePts = new HashSet<CIObj>();
		ControlFlowGraph cfg = meth.getCFG();
		
		RegisterFactory rf = cfg.getRegisterFactory();
		//r0: this pointer.
		Register r0 = rf.get(0);
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

		if(!vhRegs.contains(r0))
			return false;
		
		CIObj r0Pts = cipa.pointsTo(r0);

		//points-to set of r0.f?
		CIObj rootPts = cipa.pointsTo(r0Pts, field);
		if(rootPts.pts.isEmpty())
			return false;
		
		reachablePts.add(rootPts);
		//Collect all the reachable pt objs from root.
		Set<jq_Field> drvFields = getAllLoadFields(meth);
		boolean changed = true;
		while(changed) {
			Set<CIObj> newSet = new HashSet<CIObj>();
			for (CIObj pt : reachablePts) {
				for (jq_Field f : drvFields) {
					CIObj subPts = cipa.pointsTo(pt, f);
					if (subPts.pts.isEmpty())
						continue;
					newSet.add(subPts);
				}
			}
			changed = reachablePts.addAll(newSet);
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
							sumRegs.add(cipa.pointsTo(v));
					}
				}
				//return values.
				if (op instanceof RETURN_A) {
					Operand operand = Return.getSrc(q);
					if (!(operand instanceof RegisterOperand))
						continue;

					RegisterOperand ro = (RegisterOperand) operand;
					Register v = ro.getRegister();
					jq_Type retType = ro.getType();
					if (retType instanceof jq_NullType
							|| retType instanceof jq_ReturnAddressType)
						continue;
					sumRegs.add(cipa.pointsTo(v));
				}
				// global static fields.
				if (op instanceof PUTSTATIC_A) {
					Operand rhso = Putstatic.getSrc(q);
					jq_Field st = Putstatic.getField(q).getField();
					if ((rhso instanceof RegisterOperand))
						sumRegs.add(cipa.pointsTo(st));
				}
			}
		}
		
		//field is reachable from r?
		for (CIObj src : sumRegs) 
			for (CIObj tgt : reachablePts)
				if (src.mayAlias(tgt))
					return true;
		
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
	
	public static Set<Register> emptyArgs = new HashSet<Register>();
	
	//checking empty pts.
	public static void checkEmptyPts(CIPAAnalysis cipa) {
		for(jq_Method meth : Env.cg.getNodes()) {
			if (meth.isAbstract())
				continue;
			ControlFlowGraph cfg = meth.getCFG();
			RegisterFactory rf = cfg.getRegisterFactory();
			int numArgs = meth.getParamTypes().length;
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				Register v = rf.get(zIdx);
				if (!v.getType().isReferenceType())
					continue;
				CIObj pObj = cipa.pointsTo(v);
				if(pObj.pts.isEmpty()) {
					System.out.println(pObj.pts + "verygood....." + v + " in " + meth + meth.isStatic());
					emptyArgs.add(v);
				}
			}
		}
		System.out.println("total good: " + emptyArgs.size());
		
	}
	
	private static final Map<Register, CIObj> reg2Pt = new HashMap<Register, CIObj>();
	
	static final Map<Pair<CIObj, jq_Field>, CIObj> fieldCache = new HashMap<Pair<CIObj, jq_Field>, CIObj>();

	public static void checkEmptyFields(CIPAAnalysis cipa) {
		ProgramRel relReachableF = (ProgramRel) ClassicProject.g().getTrgt("reachableF");
		if (!relReachableF.isOpen())
			relReachableF.load();
		
		assert relReachableF.size() > 1 : "fails to load reachableF";
		
		Iterable<jq_Field> resF = relReachableF.getAry1ValTuples();
		Set<jq_Field> reaches = SetUtils.iterableToSet(resF,
				relReachableF.size());

		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		Set<jq_Field> all = new HashSet<jq_Field>();
		int numF = domF.size();
		for (int v = 1; v < numF; v++)
			all.add(domF.get(v));
		
		Set<jq_Field> unreaches = new HashSet<jq_Field>(all);
		unreaches.removeAll(reaches);
		Env.reachesF = reaches;
		
//		for(jq_Field f : reaches) {
//			
//		}
//		
//		for(jq_Field f : unreaches) {
//			System.out.println("empty field: " + f +  " class: " + f.getDeclaringClass());
//		}


//	    int numF = domF.size();
//	    assert numF > 0 : "Fails to load domF.";
	    //0 is null in chord.
		/*for (int v = 1; v < numF; v++) {
			jq_Field field = domF.get(v);
			if(!field.getType().isReferenceType())
				continue;
			if(field.getType().getName().equals("java.lang.String")) {
				Env.emptyFields.add(field);
				continue;
			}
			
			System.out.println("Checking field: " + field + " static: " + field.isStatic() + " clz: " + field.getDeclaringClass());
			//static field: directly check its points to set.
			if(field.isStatic()) {
				CIObj pt = cipa.pointsTo(field);
				if(pt.pts.isEmpty())
					Env.emptyFields.add(field);
			} else {
				continue;
				jq_Class decClz = field.getDeclaringClass();
				boolean flag = true;
				for(int i = 0; i < decClz.getVirtualMethods().length; i++) {
					jq_Method m = decClz.getVirtualMethods()[i];
					if (!SummariesEnv.v().getReachableMethods().contains(m))
						continue;
					if (m.isAbstract())
						continue;
					ControlFlowGraph cfg = m.getCFG();
					RegisterFactory rf = cfg.getRegisterFactory();
					Register r0 = rf.get(0);
					CIObj base = null;
					if (reg2Pt.containsKey(r0))
						base = reg2Pt.get(r0);
					else {
						base = cipa.pointsTo(r0);
						reg2Pt.put(r0, base);
					}

					if (base.pts.isEmpty())
						continue;

					CIObj fPt = null;
					Pair<CIObj, jq_Field> pair = new Pair<CIObj, jq_Field>(
							base, field);
					if (fieldCache.containsKey(pair)) {
						fPt = fieldCache.get(pair);
					} else {
						fPt = cipa.pointsTo(base, field);
						fieldCache.put(pair, fPt);
					}

					if (!fPt.pts.isEmpty()) {
						flag = false;
						break;
					}
				}
				if(flag)
					Env.emptyFields.add(field);
			}
			
		}*/
		
		//dump all empty fields
//		for(jq_Field f : Env.emptyFields)
//			System.out.println("empty field: " + f +  " class: " + f.getDeclaringClass());
	}
}
