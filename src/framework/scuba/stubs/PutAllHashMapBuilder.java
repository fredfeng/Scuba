package framework.scuba.stubs;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEINTERFACE_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEVIRTUAL_A;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_V;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.UTF.Utf8;

/**
 * Stub for method "boolean putAll(Map map)" in
 * class java.util.HashMap.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class PutAllHashMapBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Type[] argTypes = m.getParamTypes();
		int n = argTypes.length;
		RegisterFactory rf = new RegisterFactory(0, n);
		for (int i = 0; i < n; i++) {
			jq_Type t = argTypes[i];
    		rf.getOrCreateLocal(i, t);
		}
		
		jq_Type objType = jq_Type.parseType("java.lang.Object");
		jq_Type mapType = jq_Type.parseType("java.util.HashMap");
		jq_Type map2Type = jq_Type.parseType("java.util.Map");

		jq_Type colType = jq_Type.parseType("java.util.Collection");
		jq_Type itType = jq_Type.parseType("java.util.Iterator");
		jq_Type tabType = jq_Type.parseType("java.util.Hashtable$Entry[]");

		itType.load();
		colType.load();
		tabType.load();
		map2Type.load();
		mapType.load();

		//map
		Register r0 = rf.getOrCreateLocal(0, mapType);
		//map
		Register r1 = rf.getOrCreateLocal(1, mapType);
		
		//value
		Register t2 = rf.getOrCreateStack(2, objType);
		
		//value
		Register t3 = rf.getOrCreateStack(3, objType);

//		get:(Ljava/lang/Object;)Ljava/lang/Object;@java.util.HashMap
//        jq_NameAndDesc ndOfRun = new jq_NameAndDesc("get", "(Ljava/lang/Object;)Ljava/lang/Object");
//        jq_Method getVal = ((jq_Class)map2Type).getDeclaredMethod("get:(Ljava/lang/Object;)Ljava/lang/Object;@java.util.Map");
//        MethodOperand mo = new MethodOperand(getVal);
        
        
//        put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;@java.util.HashMap
        jq_NameAndDesc ndOfRun2 = new jq_NameAndDesc("put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object");
        jq_Method putVal = ((jq_Class)mapType).getDeclaredInstanceMethod(ndOfRun2);
        MethodOperand mo2 = new MethodOperand(putVal);


		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 1, null);
		
//		Quad q1 = New.create(1, bb, New.NEW.INSTANCE, new RegisterOperand(t2,
//				objType), new TypeOperand(objType));
//		Quad q2 = Invoke.create(1, bb, INVOKEINTERFACE_A.INSTANCE, null, mo2, 2);
//		RegisterOperand[] t = { new RegisterOperand(r1, mapType),
//				new RegisterOperand(t2, objType) };
//		ParamListOperand po = new ParamListOperand(t);
//		Invoke.setParamList(q2, po);
//		Invoke.setDest(q2, new RegisterOperand(t3, objType));
//
//		Quad q3 = Invoke
//				.create(1, bb, INVOKEVIRTUAL_A.INSTANCE, null, mo2, 3);
//		RegisterOperand[] t2Arr = { new RegisterOperand(r0, mapType),
//				new RegisterOperand(t2, objType),
//				new RegisterOperand(t3, objType) };
//		ParamListOperand po2 = new ParamListOperand(t2Arr);
//		Invoke.setParamList(q3, po2);
		Quad ret = Return.create(2, bb, RETURN_V.INSTANCE);
        
//		bb.appendQuad(q1);
//		bb.appendQuad(q2);
//		bb.appendQuad(q3);
		bb.appendQuad(ret);
		BasicBlock entry = cfg.entry();
		BasicBlock exit = cfg.exit();
		bb.addPredecessor(entry);
		bb.addSuccessor(exit);
		entry.addSuccessor(bb);
		exit.addPredecessor(bb);
		m.unsynchronize();
		return cfg;
	}
}
