package framework.scuba.stubs;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Putfield;
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
public class PutTreeMapBuilder implements ICFGBuilder {
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
		jq_Type mapType = jq_Type.parseType("java.util.TreeMap");
		jq_Type mapEntryType = jq_Type.parseType("java.util.TreeMap$Entry");
		assert mapEntryType != null;

		Register r0 = rf.getOrCreateLocal(0, mapType);
		RegisterOperand ro0 = new RegisterOperand(r0, mapType);
		//key
		Register r1 = rf.getOrCreateLocal(1, objType);
		RegisterOperand ro1 = new RegisterOperand(r1, objType);
		//value
		Register r2 = rf.getOrCreateLocal(2, objType);
		RegisterOperand ro2 = new RegisterOperand(r2, objType);
		
		Register t2 = rf.getOrCreateStack(2, objType);
		RegisterOperand to2 = new RegisterOperand(t2, mapEntryType);
		
		Register t3 = rf.getOrCreateStack(3, objType);
		RegisterOperand to3 = new RegisterOperand(t3, objType);
		
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 3, null);
        mapEntryType.load();

		jq_Field val = ((jq_Class)mapEntryType).getDeclaredField("value");
		jq_Field key = ((jq_Class)mapEntryType).getDeclaredField("key");
        jq_NameAndDesc ndOfRun = new jq_NameAndDesc("setValue", "(Ljava/lang/Object;)Ljava/lang/Object;");
        jq_Method setVal = ((jq_Class)mapEntryType).getDeclaredInstanceMethod(ndOfRun);
        MethodOperand mo = new MethodOperand(setVal);
		jq_Field root = ((jq_Class)mapType).getDeclaredField("root");
		assert(root != null);
		assert(key != null);
		assert(val != null);

		FieldOperand foRoot = new FieldOperand(root);
		FieldOperand foKey = new FieldOperand(key);
		FieldOperand foVal = new FieldOperand(val);
//		 GETFIELD_A T15, R0, .root
		Quad q1 = Getfield.create(0, bb, Getfield.GETFIELD_A.INSTANCE, to2, ro0, foRoot, null);
		
//		Quad q2 = Getfield.create(0, bb, Getfield.GETFIELD_A.INSTANCE, to2, ro0, foRoot, null);

//			3: PUTFIELD_A T15, .key, R1
//		Quad q2 = Putfield.create(1, bb, Putfield.PUTFIELD_A.INSTANCE, to2, foKey, ro1, null);
//			3: PUTFIELD_A T15, .value, R2
//		Quad q3 = Putfield.create(2, bb, Putfield.PUTFIELD_A.INSTANCE, to2, foVal, ro2, null);
        Quad q3 = Invoke.create(2, bb, Invoke.INVOKEVIRTUAL_V.INSTANCE, null, mo, setVal.getParamTypes().length);
        RegisterOperand[] t = {to2,ro2};
        ParamListOperand po = new ParamListOperand(t);
        Invoke.setParamList(q3, po);
		
		Quad ret = Return.create(3, bb, RETURN_A.INSTANCE, ro2);
		
//		setValue:(Ljava/lang/Object;)Ljava/lang/Object;@java.util.TreeMap$Entry
		bb.appendQuad(q1);
//		bb.appendQuad(q2);
		bb.appendQuad(q3);
		bb.appendQuad(ret);
		BasicBlock entry = cfg.entry();
		BasicBlock exit = cfg.exit();
		bb.addPredecessor(entry);
		bb.addSuccessor(exit);
		entry.addSuccessor(bb);
		exit.addPredecessor(bb);
		return cfg;
	}

}
