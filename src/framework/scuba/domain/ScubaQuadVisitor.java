package framework.scuba.domain;

import java.util.ArrayList;
import java.util.List;

import chord.program.Program;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.controller.SummaryController;
import framework.scuba.domain.AbstractHeap.VariableType;
import framework.scuba.helper.G;
import framework.scuba.utils.StringUtil;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.ALoad.ALOAD_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEINTERFACE_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKESTATIC_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEVIRTUAL_A;
import joeq.Compiler.Quad.Operator.Move.MOVE_A;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class ScubaQuadVisitor extends QuadVisitor.EmptyVisitor {

	protected SummaryController sumController;
	protected Summary sum;


	public ScubaQuadVisitor(SummaryController controller, Summary summary) {
		sumController = controller;
		sum = summary;
	}
	
	public void setSummary(Summary s) {
		sum = s;
	}
	
	// no-op.
	public void visitALength(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	// perform array smashing. Use assign to handle array store/load.
	// y = x[1];
	public void visitALoad(Quad stmt) {
		// only handle ALOAD_A.
		if (!(stmt.getOperator() instanceof ALOAD_A))
			return;

		jq_Method meth = stmt.getMethod();
		if (ALoad.getDest(stmt) instanceof RegisterOperand) {
			RegisterOperand rhs = (RegisterOperand) ALoad.getBase(stmt);
			RegisterOperand lhs = (RegisterOperand) ALoad.getDest(stmt);

			VariableType lvt = sumController.getVarType(stmt.getMethod(),
					lhs.getRegister());
			VariableType rvt = sumController.getVarType(stmt.getMethod(),
					rhs.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);

			flag = sum.getAbsHeap().handleALoadStmt(meth.getDeclaringClass(), meth,
					lhs.getRegister(), lvt, lhs.getType(),
					rhs.getRegister(), rvt, rhs.getType());

			sum.getAbsHeap().markChanged(flag);

		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}
	}

	// x[1] = y
	public void visitAStore(Quad stmt) {
		jq_Method meth = stmt.getMethod();

		if (AStore.getValue(stmt) instanceof RegisterOperand) {
			RegisterOperand lhs = (RegisterOperand) AStore.getBase(stmt);
			RegisterOperand rhs = (RegisterOperand) AStore.getValue(stmt);

			VariableType lvt = sumController.getVarType(stmt.getMethod(),
					lhs.getRegister());
			VariableType rvt = sumController.getVarType(stmt.getMethod(),
					rhs.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);

			flag = sum.getAbsHeap().handleAStoreStmt(meth.getDeclaringClass(), meth,
					lhs.getRegister(), lvt, lhs.getType(),
					rhs.getRegister(), rvt, rhs.getType());

			sum.getAbsHeap().markChanged(flag);

		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction");
			}
		}
	}

	// no-op.
	public void visitBinary(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	// no-op.
	public void visitBoundsCheck(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	// no-op.
	public void visitBranch(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	// treat it as assignment.
	public void visitCheckCast(Quad stmt) {
		Operand rx = CheckCast.getSrc(stmt);
		if (rx instanceof RegisterOperand) {
			jq_Method meth = stmt.getMethod();
			RegisterOperand ro = (RegisterOperand) rx;
			if (ro.getType().isReferenceType()) {
				Register r = ro.getRegister();
				RegisterOperand lo = CheckCast.getDest(stmt);
				Register l = lo.getRegister();

				VariableType lvt = sumController.getVarType(stmt.getMethod(), l);
				VariableType rvt = sumController.getVarType(stmt.getMethod(), r);
				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
						false, false);
				flag = sum.getAbsHeap().handleCheckCastStmt(
						meth.getDeclaringClass(), meth, l, lvt,
						lo.getType(), r, rvt, ro.getType());
				sum.getAbsHeap().markChanged(flag);
			}
		}

	}

	// v1 = v2.f
	public void visitGetfield(Quad stmt) {
		FieldOperand field = Getfield.getField(stmt);

		if (field.getField().getType() instanceof jq_Reference) {
			assert (stmt.getOperator() instanceof Getfield);
			RegisterOperand lhs = Getfield.getDest(stmt);

			RegisterOperand rhsBase = (RegisterOperand) Getfield
					.getBase(stmt);
			jq_Method meth = stmt.getMethod();
			VariableType lvt = sumController.getVarType(stmt.getMethod(),
					lhs.getRegister());
			VariableType rvt = sumController.getVarType(stmt.getMethod(),
					rhsBase.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = sum.getAbsHeap().handleLoadStmt(meth.getDeclaringClass(), meth,
					lhs.getRegister(), lvt, lhs.getType(),
					rhsBase.getRegister(), field.getField(), rvt,
					rhsBase.getType());

			sum.getAbsHeap().markChanged(flag);

		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}
	}

	// v = A.f.
	public void visitGetstatic(Quad stmt) {

		FieldOperand field = Getstatic.getField(stmt);

		if (field.getField().getType() instanceof jq_Reference) {
			jq_Method meth = stmt.getMethod();
			RegisterOperand lhs = Getstatic.getDest(stmt);

			jq_Class encloseClass = field.getField().getDeclaringClass();
			VariableType lvt = sumController.getVarType(stmt.getMethod(),
					lhs.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = sum.getAbsHeap().handleStatLoadStmt(meth.getDeclaringClass(),
					meth, lhs.getRegister(), lvt, lhs.getType(),
					encloseClass, field.getField());

			sum.getAbsHeap().markChanged(flag);

		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}
	}

	// no-op.
	public void visitInstanceOf(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	public void visitInvoke(Quad stmt) {

		StringUtil.reportInfo("[Handle invoke] " + stmt);

		long startInstCallsite = System.nanoTime();

		// get rhs in the factory (maybe we do not need to)
		assert (stmt.getOperator() instanceof Invoke);
		// the callsite's belonging method
		jq_Method meth = stmt.getMethod();
		// retrieve the summaries of the potential callees
		// getSumCstPairList returns only the summaries for methods that
		// have been analyzed
		List<Pair<Summary, BoolExpr>> calleeSumCstPairs = sumController
				.getSumCstPairList(stmt, sum);
		
		MemLocInstn4Method memLocInstnResult = sum.getMemLocInstnResult();

		if (G.dbgPermission) {
			StringUtil.reportInfo("dbgPermission: "
					+ "========================================");
			StringUtil.reportInfo("dbgPermission: " + "call site: " + stmt);
			StringUtil.reportInfo("dbgPermission: "
					+ "resolved number of callees: "
					+ calleeSumCstPairs.size());
		}

		if (G.tuning)
			StringUtil.reportInfo("handle callsite: " + stmt + " In "
					+ stmt.getMethod() + " Size: "
					+ calleeSumCstPairs.size());

		if (G.debug4Invoke) {
			System.out
					.println("[Debug4Invoke] Retrieving summaries for callees...");
			System.out.println("[Debug4Invoke] Size: "
					+ calleeSumCstPairs.size());
		}

		// iterate all summaries of all the potential callees
		for (Pair<Summary, BoolExpr> calleeSumCst : calleeSumCstPairs) {

			// the summary of the callee
			Summary calleeSum = calleeSumCst.val0;
			// the constraint for calling that callee
			BoolExpr hasTypeCst = calleeSumCst.val1;

			// we should only get non-null summary for getSumCstPairList
			assert (calleeSum != null) : "null summary!";
			// we should only get summaries of methods that have been
			// analyzed which is guaranteed by getSumCstPairList
			assert (calleeSum.getFormals() != null) : "null formals list!";
			assert (hasTypeCst != null) : "invalid has type constraint!";

			// get the memory location instantiation for the callee
			MemLocInstnItem item = memLocInstnResult
					.get(new Pair<Quad, jq_Method>(stmt, calleeSum
							.getMethod()));

			// if has not been cached
			if (item == null) {
				item = new MemLocInstnItem(meth, stmt,
						calleeSum.getMethod(), memLocInstnResult);
				memLocInstnResult.put(new Pair<Quad, jq_Method>(stmt,
						calleeSum.getMethod()), item);

				// fill the formal-to-actual mapping
				ParamListOperand actuals = Invoke.getParamList(stmt);
				List<StackObject> actualsMemLoc = new ArrayList<StackObject>();
				// mapping the actuals in the call site of the caller to the
				// locations in the caller's heap
				for (int i = 0; i < actuals.length(); i++) {
					RegisterOperand ro = actuals.get(i);
					Register v = ro.getRegister();
					if (sumController.getVarType(meth, v) == VariableType.PARAMEMTER) {
						actualsMemLoc.add(sum.getAbsHeap().getParamElem(
								meth.getDeclaringClass(), meth, v,
								ro.getType()));
					} else if (sumController.getVarType(meth, v) == VariableType.LOCAL_VARIABLE) {
						actualsMemLoc.add(sum.getAbsHeap().getLocalVarElem(
								meth.getDeclaringClass(), meth, v,
								ro.getType()));
					} else {
						// actuals can be primitives, we use constants to
						// denote those (we do not map formals to constants)
						actualsMemLoc.add(PrimitiveElem.getPrimitiveElem());
					}
				}
				// fill the formal-to-actual mapping
				assert (calleeSum.getFormals().size() == actualsMemLoc
						.size()) : "unmatched actuals and formals list!";

				item.initFormalToActualMapping(calleeSum.getFormals(),
						actualsMemLoc);
				// fill the return-value mapping
				// ONLY for x = v.foo(a1, a2)
				Operator opr = stmt.getOperator();
				if (opr instanceof INVOKESTATIC_A
						|| opr instanceof INVOKEVIRTUAL_A
						|| opr instanceof INVOKEINTERFACE_A
						|| Invoke.getDest(stmt) != null) {

					RegisterOperand ro = Invoke.getDest(stmt);
					StackObject sObj = sumController.getMemLocation(
							meth.getDeclaringClass(), meth,
							ro.getRegister(), ro.getType(), sum);
					assert (sObj != null) : "Fails to locate the right heap obj.";
					item.initReturnToLHS(calleeSum.getRetValue(), sObj);
				}
			}
			if (G.tuning)
				StringUtil.reportInfo("calleeSum Info: "
						+ calleeSum.getMethod());
			// instantiation the edges in the callee's heap

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);

			// using smart skip for callee instantiation
			if (SummariesEnv.v().smartSkip) {
				if (sum.inSmartSkip(item)) {
					// for locals to propagate
					if (SummariesEnv.v().jump) {
						if (sum.inJumpInstnSet(calleeSum)) {
							if (G.dbgCache) {
								System.out.println("[dbgCache] "
										+ "skipping the whole method!");
							}
							continue;
						}
					}
				}
			}

			if (G.dbgAntlr) {
				StringUtil.reportInfo("[dbgAntlr] " + " there are ["
						+ calleeSum.getAbsHeap().size()
						+ "] edges in the callee heap" + " of method "
						+ calleeSum.getMethod() + " caller Id: ["
						+ G.IdMapping.get(sum) + "]");
				StringUtil.reportInfo("[dbgAntlr] " + "calee Id: "
						+ G.IdMapping.get(calleeSum));
			}

			// add this method into the callee's depSet because callee's
			// summary effects this method
			calleeSum.jumpEffectSet.add(sum);

			flag = sum.getAbsHeap().handleInvokeStmt(meth.getDeclaringClass(), meth,
					stmt.getID(), calleeSum.getAbsHeap(), item, hasTypeCst);

			if (SummariesEnv.v().smartSkip) {
				sum.addSmartSkip(item);
			}
			if (SummariesEnv.v().jump) {
				sum.addJumpInstnSet(calleeSum);
			}

			sum.getAbsHeap().markChanged(flag);

		}
		if (G.debug4Sum) {
			if (calleeSumCstPairs.isEmpty()) {
				System.out
						.println("[Debug4Sum] Either there is no available summaries "
								+ "or it is not a processable statement!");
			}
		}

		long endInstCallsite = System.nanoTime();
		if (G.tuning)
			StringUtil.reportSec("Time to instantiate callsite: " + stmt,
					startInstCallsite, endInstCallsite);
		
		//regression test.
		jq_Method callee = Invoke.getMethod(stmt).getMethod();
		if (callee.toString().equals(
				"check_alias:(Ljava/lang/Object;Ljava/lang/Object;)V@A")) {
			Register r1 = Invoke.getParam(stmt, 0).getRegister();
			Register r2 = Invoke.getParam(stmt, 1).getRegister();
			SummariesEnv.v().addAliasPairs(meth, r1, r2);
		}

	}

	// no sure whether we should mark this as no op.
	public void visitMemLoad(Quad stmt) {
		System.out.println(stmt);
		assert false : "MemLoad";
	}

	// no sure whether we should mark this as no op.
	public void visitMemStore(Quad stmt) {
		System.out.println(stmt);
		assert false : "MemStore";
	}

	// no-op.
	public void visitMonitor(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	// v1 = v2
	public void visitMove(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		// 1. the first clause make sure only if it's ref type.
		// 2. the second clause is to ignore string assignment like x="Hi"
		// The question is, do we need to handle string operation? TODO
		if ((stmt.getOperator() instanceof MOVE_A)
				&& (Move.getSrc(stmt) instanceof RegisterOperand)) {
			RegisterOperand rhs = (RegisterOperand) Move.getSrc(stmt);
			RegisterOperand lhs = (RegisterOperand) Move.getDest(stmt);

			VariableType lvt = sumController.getVarType(stmt.getMethod(),
					lhs.getRegister());
			VariableType rvt = sumController.getVarType(stmt.getMethod(),
					rhs.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = sum.getAbsHeap().handleAssignStmt(meth.getDeclaringClass(), meth,
					lhs.getRegister(), lvt, lhs.getType(),
					rhs.getRegister(), rvt, rhs.getType());

			sum.getAbsHeap().markChanged(flag);
		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}
	}

	// v1 = new A();
	public void visitNew(Quad stmt) {
		assert (stmt.getOperator() instanceof New);

		jq_Class exception = (jq_Class) Program.g().getClass(
				"java.lang.Exception");

		jq_Method meth = stmt.getMethod();
		TypeOperand to = New.getType(stmt);

		if (to.getType() instanceof jq_Class) {
			jq_Class clz = (jq_Class) to.getType();
			// do not handle new exception.
			if (clz.extendsClass(exception))
				return;
		}

		RegisterOperand rop = New.getDest(stmt);
		VariableType vt = sumController.getVarType(meth, rop.getRegister());

		Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
				false);
		flag = sum.getAbsHeap().handleNewStmt(stmt.getMethod().getDeclaringClass(),
				meth, rop.getRegister(), vt, rop.getType(), to.getType(),
				stmt, stmt.getID());

		sum.getAbsHeap().markChanged(flag);
	}

	// v = new Node[10][10]
	public void visitMultiNewArray(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		TypeOperand to = MultiNewArray.getType(stmt);
		RegisterOperand rop = MultiNewArray.getDest(stmt);
		VariableType vt = sumController.getVarType(meth, rop.getRegister());
		ParamListOperand plo = MultiNewArray.getParamList(stmt);

		Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
				false);
		flag = sum.getAbsHeap().handleMultiNewArrayStmt(meth.getDeclaringClass(),
				meth, rop.getRegister(), vt, rop.getType(), to.getType(),
				plo.length(), stmt, stmt.getID());

		sum.getAbsHeap().markChanged(flag);
	}

	// v = new Node[10];
	public void visitNewArray(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		TypeOperand to = NewArray.getType(stmt);

		RegisterOperand rop = NewArray.getDest(stmt);
		VariableType vt = sumController.getVarType(meth, rop.getRegister());

		Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
				false);
		flag = sum.getAbsHeap().handleNewArrayStmt(meth.getDeclaringClass(), meth,
				rop.getRegister(), vt, rop.getType(), to.getType(), stmt,
				stmt.getID());

		sum.getAbsHeap().markChanged(flag);
	}

	// no-op.
	public void visitNullCheck(Quad stmt) {

	}

	// we translate phinode into a set of assignments.
	// PHI node: PHI T5, (T3, T4), { BB3, BB4 }
	public void visitPhi(Quad stmt) {
		jq_Method meth = stmt.getMethod();

		boolean tmp = false; // just for dbg
		if (Phi.getDest(stmt) instanceof RegisterOperand) {
			Pair<Boolean, Boolean> sig = new Pair<Boolean, Boolean>(false,
					false);
			RegisterOperand lhs = Phi.getDest(stmt);
			VariableType lvt = sumController.getVarType(meth, lhs.getRegister());

			for (RegisterOperand rhs : stmt.getOperator().getUsedRegisters(
					stmt)) {
				// PHI T5, (null, T4), { BB3, BB4 }
				if (rhs == null)
					continue;

				tmp = true;
				VariableType rvt = sumController.getVarType(meth, rhs.getRegister());
				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
						false, false);
				flag = sum.getAbsHeap().handleAssignStmt(meth.getDeclaringClass(),
						meth, lhs.getRegister(), lvt, lhs.getType(),
						rhs.getRegister(), rvt, rhs.getType());
				sig.val0 = flag.val0 | sig.val0;
				sig.val1 = flag.val1 | sig.val1;
			}

			sum.getAbsHeap().markChanged(sig);
		}

		if (!tmp) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

	}

	// v1.f = v2
	public void visitPutfield(Quad stmt) {
		FieldOperand field = Putfield.getField(stmt);
		if (field.getField().getType() instanceof jq_Reference) {
			assert (stmt.getOperator() instanceof Putfield);
			jq_Method meth = stmt.getMethod();

			Operand rhso = Putfield.getSrc(stmt);
			if (rhso instanceof RegisterOperand) {
				RegisterOperand rhs = (RegisterOperand) rhso;
				RegisterOperand lhs = (RegisterOperand) Putfield
						.getBase(stmt);

				VariableType lvt = sumController.getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = sumController.getVarType(stmt.getMethod(),
						rhs.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
						false, false);

				flag = sum.getAbsHeap().handleStoreStmt(meth.getDeclaringClass(),
						meth, lhs.getRegister(), lvt, lhs.getType(),
						field.getField(), rhs.getRegister(), rvt,
						rhs.getType());

				sum.getAbsHeap().markChanged(flag);
			}
		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}
	}

	// A.f = b;
	public void visitPutstatic(Quad stmt) {

		FieldOperand field = Putstatic.getField(stmt);
		if (field.getField().getType() instanceof jq_Reference) {
			jq_Method meth = stmt.getMethod();
			Operand rhso = Putstatic.getSrc(stmt);
			jq_Class encloseClass = field.getField().getDeclaringClass();

			if (rhso instanceof RegisterOperand) {
				RegisterOperand rhs = (RegisterOperand) rhso;

				VariableType rvt = sumController.getVarType(stmt.getMethod(),
						rhs.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
						false, false);
				flag = sum.getAbsHeap().handleStaticStoreStmt(
						meth.getDeclaringClass(), meth, encloseClass,
						field.getField(), rhs.getRegister(), rvt,
						rhs.getType());
				sum.getAbsHeap().markChanged(flag);
			}
		} else {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

	}

	public void visitReturn(Quad stmt) {
		// if (stmt.getOperator() instanceof RETURN_A) {
		Operand operand = Return.getSrc(stmt);
		if (!(operand instanceof RegisterOperand))
			return;
		Register ret = ((RegisterOperand) operand).getRegister();
		jq_Method meth = stmt.getMethod();
		jq_Class clazz = meth.getDeclaringClass();
		VariableType type = sumController.getVarType(meth, ret);

		Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
				false);
		flag = sum.getAbsHeap().handleRetStmt(clazz, meth, ret, type,
				meth.getReturnType());
		sum.getAbsHeap().markChanged(flag);

		// if (retValue == null) {
		assert (sum.getAbsHeap().contains(new RetElem(clazz, meth, meth
				.getReturnType()))) : ""
				+ "the return value should be contained in the heap!";
		RetElem retValue = sum.getAbsHeap().getRetElem(clazz, meth, meth.getReturnType());
		sum.setRetValue(retValue);
	}

	// no sure whether we should mark this as no op.
	public void visitSpecial(Quad stmt) {
		System.out.println(stmt);
		assert false : "Special stmt that we havn't consider. Abort.";
	}

	// no-op.
	public void visitStoreCheck(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}

	// no sure whether we should mark this as no op.
	public void visitUnary(Quad stmt) {
	}

	// no-op.
	public void visitZeroCheck(Quad stmt) {
		if (G.debug4Sum) {
			System.out
					.println("[Debug4Sum] Not a processable instruction!");
		}
	}


}
