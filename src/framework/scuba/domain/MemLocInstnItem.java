package framework.scuba.domain;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import chord.program.Program;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ConstraintManager;

public class MemLocInstnItem {

	// this maps from the abstract memory locations in the heap of the callee to
	// the abstract memory locations in the heap of the caller
	// callee locations --> caller locations
	final protected MemLocInstnItemCache memLocInstnCache;

	final protected MemLocInstn4Method result;

	// the caller that this instantiation belongs to
	final protected jq_Method caller;

	// the call site that this instantiation is for
	final protected Quad callsite;

	// the callee that this instantiation is for
	final protected jq_Method callee;

	// hasRet = true: there is a location mapped in the caller's heap
	protected boolean hasRet = false;

	public MemLocInstnItem(jq_Method caller, Quad callsite, jq_Method callee,
			MemLocInstn4Method result) {
		this.caller = caller;
		this.callsite = callsite;
		this.callee = callee;
		this.result = result;
		memLocInstnCache = new MemLocInstnItemCache(this);
	}

	public jq_Method getCaller() {
		return caller;
	}

	public jq_Method getCallee() {
		return callee;
	}

	public Quad getCallSite() {
		return callsite;
	}

	public int size() {
		return memLocInstnCache.size();
	}

	// given a location in the callee's heap, remove the cache item in the cache
	public MemLocInstnSet remove(AbsMemLoc loc) {
		return memLocInstnCache.remove(loc);
	}

	public void print() {
		System.out
				.println("\n----------------Mem Loc Instantion-----------------");
		System.out.println("Caller: " + caller);
		System.out.println("Callee: " + callee);
		System.out.println("Call site: " + callsite);
		for (AbsMemLoc loc : memLocInstnCache.keySet()) {
			System.out.println("*****************************");
			System.out.println("Location: " + loc + " is instantiated to:");
			Map<AbsMemLoc, BoolExpr> ret = memLocInstnCache.get(loc)
					.getInstnLocSet();
			for (AbsMemLoc loc1 : ret.keySet()) {
				System.out.println("Location: " + loc1);
				System.out.println("Constraint: " + ret.get(loc1));
			}
		}
		System.out.println("*****************************");
		System.out.println("\n-----------------------------------------");

	}

	// initialized the formal-to-actual mapping
	public void initFormalToActualMapping(List<ParamElem> formals,
			List<StackObject> actuals) {

		assert (actuals.size() == formals.size()) : "unmatched formal-to-actual mapping!";

		for (int i = 0; i < actuals.size(); i++) {
			StackObject actual = actuals.get(i);
			if (actual instanceof PrimitiveElem) {
				// we just ignore the primitives
				continue;
			} else {
				ParamElem formal = formals.get(i);
				memLocInstnCache.put(formal, new MemLocInstnSet(actual,
						ConstraintManager.genTrue()));
			}
		}
	}

	// initialize the return value and lhs mapping
	public void initReturnToLHS(RetElem ret, StackObject lhs) {
		hasRet = true;
		memLocInstnCache.put(ret,
				new MemLocInstnSet(lhs, ConstraintManager.genTrue()));
	}

	// a wrapper method for memory location instantiation
	public MemLocInstnSet instnMemLoc(AbsMemLoc loc, AbstractHeap callerHeap,
			ProgramPoint point) {
		MemLocInstnSet ret = null;
		if (SummariesEnv.v().useMemLocInstnCache) {
			Set<AccessPath> orgs = new HashSet<AccessPath>();
			ret = instnMemLocUsingCache(orgs, loc, callerHeap, point);
		} else {
			ret = instnMemLocNoCache(loc, callerHeap, point);
		}
		return ret;
	}

	// orgs: the callee locations that depend on the instantiated locations
	// loc: the callee location we want to instantiate
	protected MemLocInstnSet instnMemLocUsingCache(Set<AccessPath> orgs,
			AbsMemLoc loc, AbstractHeap callerHeap, ProgramPoint point) {

		MemLocInstnSet ret = memLocInstnCache.get(loc);

		if (loc instanceof ParamElem) {
			assert (ret != null) : "parameters should have been instantiated"
					+ " when the first time init the instantiation";
		}

		if (loc instanceof RetElem) {
			if (hasRet) {
				assert (ret != null) : "return value should have been instantiated"
						+ " when the first time init the instantiation!";
			} else {
				assert (ret == null) : "if there is no LHS in the call site, "
						+ "we cannot instantiate the return value";
			}
		}

		if (loc instanceof LocalVarElem || loc instanceof ParamElem
				|| loc instanceof StaticElem) {
			// this is my little cute cache
			if (ret != null) {
				return ret;
			}
			// not hitting cache
			ret = new MemLocInstnSet(loc, ConstraintManager.genTrue());
			// put into the map
			memLocInstnCache.put(loc, ret);
		} else if (loc instanceof RetElem) {
			if (hasRet) {
				assert (ret != null) : "return value should be mapped"
						+ " the first time init the instantiation";
				return ret;
			} else {
				assert (ret == null) : "return value should"
						+ " not be instantiated if there is no LHS!";
				return null;
			}
		} else if (loc instanceof AllocElem) {
			// we also instantiated allocElem only once!
			// wow! I guess it will save a LOT of time by doing this
			if (ret != null) {
				return ret;
			}
			// not hitting the cache
			if (SummariesEnv.v().allocDepth == 0
					|| ((AllocElem) loc).length() < SummariesEnv.v().allocDepth) {

				if (((AllocElem) loc).contains(point)) {
					// to avoid non-termination
					AllocElem allocElem = callerHeap
							.getAllocElem(((AllocElem) loc));
					ret = new MemLocInstnSet(allocElem,
							ConstraintManager.genTrue());
				} else {
					if (SummariesEnv.v().dynAlloc) {
						// using dynamic allocation
						if (!SummariesEnv.v().getLibMeths()
								.contains(point.getBelongingMethod())) {
							// caller is NOT in java library
							AllocElem allocElem = callerHeap.getAllocElem(
									((AllocElem) loc), point);
							ret = new MemLocInstnSet(allocElem,
									ConstraintManager.genTrue());
						} else {
							// caller is in java library
							AllocElem allocElem = callerHeap
									.getAllocElem(((AllocElem) loc));
							ret = new MemLocInstnSet(allocElem,
									ConstraintManager.genTrue());
						}
					} else {
						// not using dynamic allocation
						// just append the context without differentiating
						AllocElem allocElem = callerHeap.getAllocElem(
								((AllocElem) loc), point);
						ret = new MemLocInstnSet(allocElem,
								ConstraintManager.genTrue());
					}
				}
			} else if (((AllocElem) loc).length() == SummariesEnv.v().allocDepth) {
				// TODO
				// AllocElem allocElem = (AllocElem) loc;
				AllocElem allocElem = callerHeap.getAllocElem((AllocElem) loc);
				ret = new MemLocInstnSet(allocElem, ConstraintManager.genTrue());
			} else {
				assert false : "wrong!";
			}
			// put into the map
			memLocInstnCache.put(loc, ret);
		} else if (loc instanceof AccessPath) {
			if (ret != null) {
				return ret;
			}

			// instantiation for smashed access path

			AbsMemLoc base = ((AccessPath) loc).getBase();
			FieldElem field = ((AccessPath) loc).getField();
			assert (!orgs.contains((AccessPath) loc)) : "Location " + loc
					+ " should not depend on the instantiation of"
					+ " location " + base;
			orgs.add((AccessPath) loc);
			MemLocInstnSet instnLocSet = instnMemLocUsingCache(orgs, base,
					callerHeap, point);
			assert (orgs.contains((AccessPath) loc)) : "orgs should contain "
					+ loc + " and we can remove it from orgs";
			orgs.remove((AccessPath) loc);
			for (AbsMemLoc loc1 : instnLocSet.keySet()) {
				result.getSum().addToDepMap(
						new Pair<AbsMemLoc, FieldElem>(loc1, field),
						new Pair<MemLocInstnItem, Set<AccessPath>>(this, orgs));
			}

			ret = callerHeap.instnLookup(instnLocSet, field);

			// a work-list algorithm for find all locations that are
			// transitively reachable from the current instantiated memory
			// locations in order to be sound
			if (SummariesEnv.v().markSmashedFields
					&& SummariesEnv.v().instnSmashedAPs) {
				if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.TYPESMASH) {
					// to avoid non-termination
					Set<AbsMemLoc> visited = new HashSet<AbsMemLoc>();
					// work list storing the locations that are candidates as
					// the instantiated locations
					LinkedHashSet<Pair<AbsMemLoc, BoolExpr>> wl = new LinkedHashSet<Pair<AbsMemLoc, BoolExpr>>();
					// the locations that can really be instantiated into
					MemLocInstnSet targets = new MemLocInstnSet();
					// initialized the work list
					if (((AccessPath) loc).isSmashed()) {
						for (AbsMemLoc loc1 : ret.keySet()) {
							BoolExpr expr1 = ret.get(loc1);
							wl.add(new Pair<AbsMemLoc, BoolExpr>(loc1, expr1));
						}
					}
					// the work list algorithm
					while (!wl.isEmpty()) {
						Pair<AbsMemLoc, BoolExpr> elem = wl.iterator().next();
						wl.remove(elem);
						AbsMemLoc canddt = elem.val0;
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getSmashedFields();
						// the smashed types
						Set<jq_Type> smashedTypes = new HashSet<jq_Type>();
						for (FieldElem f : smashedFields) {
							if (f instanceof NormalFieldElem) {
								smashedTypes.add(((NormalFieldElem) f)
										.getField().getType());
							} else if (f instanceof EpsilonFieldElem) {
								smashedTypes.add(((AccessPath) loc).getBase()
										.getType());
							} else if (f instanceof IndexFieldElem) {
								// assert false;
								smashedTypes.add(Program.g().getClass(
										"java.lang.Object"));
							} else {
								assert false;
							}
						}
						jq_Type type = null;
						if (field instanceof NormalFieldElem) {
							type = ((NormalFieldElem) field).getField()
									.getType();
						} else if (field instanceof EpsilonFieldElem) {
							type = loc.findRoot().getType();
						} else if (field instanceof IndexFieldElem) {
							type = (loc.getType() instanceof jq_Array) ? ((jq_Array) loc
									.getType()).getElementType() : Program.g()
									.getClass("java.lang.Object");
						} else {
							assert false;
						}
						assert type != null;
						BoolExpr cst = elem.val1;
						// mark as visited
						visited.add(canddt);
						// worker is a candidate
						MemLocInstnSet worker = new MemLocInstnSet(canddt, cst);

						for (FieldElem f1 : canddt.getFields()) {
							jq_Type t = null;
							if (f1 instanceof NormalFieldElem) {
								t = ((NormalFieldElem) f1).getField().getType();
							} else if (f1 instanceof EpsilonFieldElem) {
								t = canddt.findRoot().getType();
							} else if (f1 instanceof IndexFieldElem) {
								t = (canddt.getType() instanceof jq_Array) ? ((jq_Array) canddt
										.getType()).getElementType() : Program
										.g().getClass("java.lang.Object");
							} else {
								assert false;
							}
							assert t != null;
							// ignore the in-compatible fields
							if (!smashedTypes.contains(t)) {
								continue;
							}
							// if ending fields match, then this is one target
							if (type.equals(t)) {
								MemLocInstnSet next = callerHeap.instnLookup(
										worker, f1);
								targets.addAll(next);
							}
							// if f1 is smashed by ap, then add this into wl
							if (smashedTypes.contains(t)) {
								MemLocInstnSet next = callerHeap.instnLookup(
										worker, f1);
								for (AbsMemLoc loc2 : next.keySet()) {
									if (!visited.contains(loc2)) {
										BoolExpr cst2 = next.get(loc2);
										wl.add(new Pair<AbsMemLoc, BoolExpr>(
												loc2, cst2));
									}
								}
							}
						}
					}
					// then conjoin with the previous result
					ret.addAll(targets);
				} else {
					// to avoid non-termination
					Set<AbsMemLoc> visited = new HashSet<AbsMemLoc>();
					// work list storing the locations that are candidates as
					// the instantiated locations
					LinkedHashSet<Pair<AbsMemLoc, BoolExpr>> wl = new LinkedHashSet<Pair<AbsMemLoc, BoolExpr>>();
					// the locations that can really be instantiated into
					MemLocInstnSet targets = new MemLocInstnSet();
					// initialized the work list
					if (((AccessPath) loc).isSmashed()) {
						for (AbsMemLoc loc1 : ret.keySet()) {
							BoolExpr expr1 = ret.get(loc1);
							wl.add(new Pair<AbsMemLoc, BoolExpr>(loc1, expr1));
						}
					}
					// the work list algorithm
					while (!wl.isEmpty()) {
						Pair<AbsMemLoc, BoolExpr> elem = wl.iterator().next();
						wl.remove(elem);
						AbsMemLoc canddt = elem.val0;
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getSmashedFields();
						BoolExpr cst = elem.val1;
						// mark as visited
						visited.add(canddt);
						// worker is a candidate
						MemLocInstnSet worker = new MemLocInstnSet(canddt, cst);

						for (FieldElem f1 : canddt.getFields()) {
							// ignore the in-compatible fields
							if (!smashedFields.contains(f1)) {
								continue;
							}
							// if ending fields match, then this is one target
							if (field.equals(f1)) {
								MemLocInstnSet next = callerHeap.instnLookup(
										worker, f1);
								targets.addAll(next);
							}
							// if f1 is smashed by ap, then add this into wl
							if (smashedFields.contains(f1)) {
								MemLocInstnSet next = callerHeap.instnLookup(
										worker, f1);
								for (AbsMemLoc loc2 : next.keySet()) {
									if (!visited.contains(loc2)) {
										BoolExpr cst2 = next.get(loc2);
										wl.add(new Pair<AbsMemLoc, BoolExpr>(
												loc2, cst2));
									}
								}
							}
						}
					}
					// then conjoin with the previous result
					ret.addAll(targets);
				}
			}

			memLocInstnCache.put(loc, ret);
		} else {
			assert false : "wried things happen! Unknow type.";
		}

		return ret;
	}

	// this method implements the inference rules in figure 11 of the paper
	// loc is the location in the callee's heap
	protected MemLocInstnSet instnMemLocNoCache(AbsMemLoc loc,
			AbstractHeap callerHeap, ProgramPoint point) {

		MemLocInstnSet ret = memLocInstnCache.get(loc);

		if (loc instanceof ParamElem) {
			assert (ret != null) : "parameters should have been instantiated"
					+ " when the first time init the instantiation";
		}

		if (loc instanceof RetElem) {
			if (hasRet) {
				assert (ret != null) : "return value should have been instantiated"
						+ " when the first time init the instantiation!";
			} else {
				assert (ret == null) : "if there is no LHS in the call site, "
						+ "we cannot instantiate the return value";
			}
		}

		if (loc instanceof LocalVarElem || loc instanceof ParamElem
				|| loc instanceof StaticElem) {

			// this is my little cute cache
			if (ret != null) {
				return ret;
			}
			// not hitting cache
			ret = new MemLocInstnSet(loc, ConstraintManager.genTrue());
			// put into the map
			memLocInstnCache.put(loc, ret);
		} else if (loc instanceof RetElem) {
			if (hasRet) {
				assert (ret != null) : "return value should be mapped"
						+ " the first time init the instantiation";
				return ret;
			} else {
				assert (ret == null) : "return value should"
						+ " not be instantiated if there is no LHS!";
				return null;
			}
		} else if (loc instanceof AllocElem) {
			// we also instantiated allocElem only once!
			// wow! I guess it will save a LOT of time by doing this
			if (ret != null) {
				return ret;
			}
			// not hitting the cache
			if (SummariesEnv.v().allocDepth == 0
					|| ((AllocElem) loc).length() < SummariesEnv.v().allocDepth) {

				if (((AllocElem) loc).contains(point)) {
					AllocElem allocElem = callerHeap
							.getAllocElem(((AllocElem) loc));
					ret = new MemLocInstnSet(allocElem,
							ConstraintManager.genTrue());
				} else {
					AllocElem allocElem = callerHeap.getAllocElem(
							((AllocElem) loc), point);
					ret = new MemLocInstnSet(allocElem,
							ConstraintManager.genTrue());
				}
			} else if (((AllocElem) loc).length() == SummariesEnv.v().allocDepth) {

				AllocElem allocElem = callerHeap.getAllocElem((AllocElem) loc);
				ret = new MemLocInstnSet(allocElem, ConstraintManager.genTrue());
			} else {
				assert false : "wrong!";
			}
			// put into the map
			memLocInstnCache.put(loc, ret);
		} else if (loc instanceof AccessPath) {
			AbsMemLoc base = ((AccessPath) loc).getBase();
			FieldElem field = ((AccessPath) loc).getField();
			MemLocInstnSet instnLocSet = instnMemLocNoCache(base, callerHeap,
					point);
			ret = callerHeap.instnLookup(instnLocSet, field);

			// put into the map
			memLocInstnCache.put(loc, ret);

		} else {
			assert false : "wried things happen! Unknow type.";
		}

		return ret;
	}
}
