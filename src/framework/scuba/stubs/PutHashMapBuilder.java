package framework.scuba.stubs;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Primitive;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.IConstOperand;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for method "boolean put(k,v)" in
 * class java.util.HashMap.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class PutHashMapBuilder implements ICFGBuilder {
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
		Register r0 = rf.getOrCreateLocal(0, mapType);
		RegisterOperand ro0 = new RegisterOperand(r0, mapType);
		Register r1 = rf.getOrCreateLocal(1, objType);
		RegisterOperand ro1 = new RegisterOperand(r1, objType);
		Register r2 = rf.getOrCreateLocal(2, objType);
		RegisterOperand ro2 = new RegisterOperand(r2, objType);
		Register r4 = rf.getOrCreateStack(4, jq_Primitive.INT);
		RegisterOperand ro4 = new RegisterOperand(r4, jq_Primitive.INT);
		
		Register r7 = rf.getOrCreateStack(7, jq_Primitive.INT);
		RegisterOperand ro7 = new RegisterOperand(r7, jq_Primitive.INT);
		
		String methName = "addEntry:(ILjava/lang/Object;Ljava/lang/Object;I)V@java.util.HashMap";
        jq_NameAndDesc ndOfRun = new jq_NameAndDesc("addEntry", "(ILjava/lang/Object;Ljava/lang/Object;I)V");
        jq_Method addEntry = ((jq_Class)mapType).getDeclaredInstanceMethod(ndOfRun);
        MethodOperand mo = new MethodOperand(addEntry);
        
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 4, null);
		
		Quad q1 = Move.create(0, bb, Move.MOVE_I.INSTANCE, ro4, new IConstOperand(1));
		Quad q2 = Move.create(1, bb, Move.MOVE_I.INSTANCE, ro7, new IConstOperand(1));
        Quad q3 = Invoke.create(2, bb, Invoke.INVOKEVIRTUAL_V.INSTANCE, null, mo, addEntry.getParamTypes().length);
        
        RegisterOperand[] t = {ro0,ro4,ro1,ro2,ro7};
        ParamListOperand po = new ParamListOperand(t);
        Invoke.setParamList(q3, po);

		Quad ret = Return.create(3, bb, RETURN_A.INSTANCE, ro2);
		bb.appendQuad(q1);
		bb.appendQuad(q2);
		bb.appendQuad(q3);
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
	
	//17: INVOKEVIRTUAL_V addEntry:(ILjava/lang/Object;Ljava/lang/Object;I)V@java.util.HashMap, (R0, R4, R1, R2, R7)

//	4: GETFIELD_A T15, R0, .table
//	5: NEW T16, java.util.HashMap$Entry
//	8: ASTORE_A T16, T15, R4
}
