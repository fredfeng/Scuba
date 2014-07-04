package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Type;
import chord.program.Program;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ConstraintManager;

public class P2Set {

	protected Map<HeapObject, BoolExpr> p2Set = new HashMap<HeapObject, BoolExpr>();

	// this is an empty p2set which is often used in dealing with null
	public P2Set() {

	}

	public P2Set(HeapObject obj, BoolExpr constraint) {
		p2Set.put(obj, constraint);
	}

	// this is the default p2set
	public P2Set(HeapObject obj) {
		assert (obj.isArgDerived()) : obj + " is not argument derived!";
		assert (obj instanceof HeapObject) : obj + " is not a heap object!";
		p2Set.put(obj, ConstraintManager.genTrue());
	}

	public boolean isEmpty() {
		return p2Set.isEmpty();
	}

	public BoolExpr remove(HeapObject hObj) {
		return p2Set.remove(hObj);
	}

	// this join method implements the join operation described in definition 8
	// of the paper, in which it only reads other and write this.p2Set
	// this method will never get the pointer to the other p2set so do not worry
	// about modifying the other p2set by modifying this p2set
	public Pair<Boolean, Boolean> join(P2Set other, AbstractHeap absHeap,
			AbsMemLoc src, FieldElem f) {
		boolean changeHeap = false;
		boolean changeSum = false;
		jq_Type typeFilter = null;

		if (f instanceof NormalFieldElem) {
			typeFilter = ((NormalFieldElem) f).getField().getType();
		} else if (f instanceof IndexFieldElem) {
			typeFilter = ((src.getType() instanceof jq_Array) ? ((jq_Array) src
					.getType()).getElementType() : Program.g().getClass(
					"java.lang.Object"));
		} else if (f instanceof EpsilonFieldElem) {
			typeFilter = src.getType();
		} else {
			assert false : "wired thing! unknow type!";
		}
		assert typeFilter != null;

		for (HeapObject obj : other.keySet()) {

			// filtering the in-compatible types
			if (SummariesEnv.v().useTypeFilter) {
				if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.REG) {
					if (!obj.getType().isSubtypeOf(typeFilter)
							&& !typeFilter.isSubtypeOf(obj.getType())) {
						continue;
					}
				} else if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.TYPECOMPSMASH
						|| SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.TYPESMASH
						|| SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.CTRLLENGTH) {
					Set<jq_Type> set = new HashSet<jq_Type>();
					if (obj instanceof AccessPath) {
						set.addAll(((AccessPath) obj).getEndingFieldsTypes());
					} else {
						set.add(obj.getType());
					}
					boolean update = false;
					for (jq_Type t : set) {
						if (t.isSubtypeOf(typeFilter)
								|| typeFilter.isSubtypeOf(t)) {
							update = true;
							break;
						}
					}
					if (!update) {
						continue;
					}
				} else {
					assert false : "unknow decoding way!";
				}
			}

			// the conjunction operation
			if (p2Set.containsKey(obj)) {
				// obj is in both p2sets
				// directly get the other p2set's constraints
				BoolExpr otherCst = other.get(obj);

				// generate the union of the two (a shallow copy with the same
				// constraints but different instances)
				BoolExpr newCst = ConstraintManager.union(p2Set.get(obj),
						otherCst);

				// TODO
				// remove the edges with false constraints
				if (ConstraintManager.isFalse(newCst)) {
					continue;
				}
				// check whether we need to update the p2set of this heap object
				// TODO check the return value
				// we should use the equivalence checking
				if (ConstraintManager.isEqual(p2Set.get(obj), newCst))
					continue;

				p2Set.put(obj, newCst);
				// TODO
				changeHeap = true;
				if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {
					changeSum = true;
				} else if (obj.isArgDerived() || absHeap.toProp(obj)) {
					changeSum = true;
				}
			} else {
				// obj is only in other's p2set
				// AVOID directly get the constraint instance of the other
				// p2set!!!! only get the shallow copy of the other constraints

				BoolExpr otherCst = other.get(obj);
				// TODO
				// remove the edges with false constraints
				if (ConstraintManager.isFalse(otherCst)) {
					continue;
				}

				// for this case, we should add a new edge
				p2Set.put(obj, ConstraintManager.clone(other.get(obj)));
				changeHeap = true;
				if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {
					changeSum = true;
				} else if (obj.isArgDerived() || absHeap.toProp(obj)) {
					changeSum = true;
				}
			}
		}
		return new Pair<Boolean, Boolean>(changeHeap, changeSum);
	}

	// this is used for join operation not related to updating the heap
	public Pair<Boolean, Boolean> join(P2Set other, AbstractHeap absHeap) {
		boolean changeHeap = false;
		boolean changeSum = false;
		for (HeapObject obj : other.keySet()) {
			if (p2Set.containsKey(obj)) {
				// obj is in both p2sets
				// directly get the other p2set's constraints
				BoolExpr otherCst = other.get(obj);

				// generate the union of the two (a shallow copy with the same
				// constraints but different instances)
				BoolExpr newCst = ConstraintManager.union(p2Set.get(obj),
						otherCst);

				// TODO
				// remove the edges with false constraints
				if (ConstraintManager.isFalse(newCst)) {
					continue;
				}
				// check whether we need to update the p2set of this heap object
				// TODO check the return value
				// we should use the equivalence checking
				if (ConstraintManager.isEqual(p2Set.get(obj), newCst))
					continue;

				p2Set.put(obj, newCst);
				// TODO
				changeHeap = true;
				if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {
					changeSum = true;
				} else if (obj.isArgDerived() || absHeap.toProp(obj)) {
					changeSum = true;
				}
			} else {
				// obj is only in other's p2set
				// AVOID directly get the constraint instance of the other
				// p2set!!!! only get the shallow copy of the other constraints

				BoolExpr otherCst = other.get(obj);
				// TODO
				// remove the edges with false constraints
				if (ConstraintManager.isFalse(otherCst)) {
					continue;
				}

				// for this case, we should add a new edge
				p2Set.put(obj, ConstraintManager.clone(other.get(obj)));
				changeHeap = true;
				if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {
					changeSum = true;
				} else if (obj.isArgDerived() || absHeap.toProp(obj)) {
					changeSum = true;
				}
			}
		}
		return new Pair<Boolean, Boolean>(changeHeap, changeSum);
	}

	// this project method implements the projection operation described in
	// definition 9 of the paper, in which it writes this.p2Set and only reads
	// the other constraint
	// this method will never modify the other constraints, either not get the
	// pointer to the other constraint
	public P2Set project(BoolExpr otherCst) {
		for (HeapObject obj : p2Set.keySet()) {
			// this newCst is a copy with the same content but different
			// constraint instances
			BoolExpr newCst = ConstraintManager.intersect(p2Set.get(obj),
					otherCst);
			p2Set.put(obj, newCst);
		}
		return this;
	}

	// replace the previous value (constraint) in the map
	// if previously not in the map, return null
	// otherwise, return the previous value mapped by key (obj)
	// interesting!
	public BoolExpr put(HeapObject obj, BoolExpr constraint) {
		return p2Set.put(obj, constraint);
	}

	public boolean contains(HeapObject obj) {
		return p2Set.containsKey(obj);
	}

	public boolean containsAll(Set<AbsMemLoc> objs) {
		// actually objs is Set<HeapObject>
		return p2Set.keySet().containsAll(objs);
	}

	public Set<HeapObject> keySet() {
		return p2Set.keySet();
	}

	// return null or return true constraint?
	public BoolExpr get(HeapObject obj) {
		// if ptSet contains obj then return that obj, otherwise return null
		return p2Set.get(obj);
	}

	// do a shallow copy (only shallowly copying the constraints)
	public P2Set clone() {
		P2Set ret = new P2Set();

		for (HeapObject obj : p2Set.keySet()) {
			ret.put(obj, p2Set.get(obj));
		}

		return ret;
	}

	public int size() {
		return p2Set.size();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof P2Set))
			return false;

		P2Set otherPT = (P2Set) other;

		if (p2Set.size() != otherPT.size())
			return false;

		for (HeapObject hObj : p2Set.keySet()) {
			if (otherPT.contains(hObj)) {
				BoolExpr otherCst = otherPT.get(hObj);
				BoolExpr thisCst = p2Set.get(hObj);
				if (!thisCst.equals(otherCst))
					return false;
			} else {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		if (p2Set.isEmpty())
			return 0;

		int ret = 0;
		int i = 0;
		int range = 3;
		for (HeapObject hObj : p2Set.keySet()) {
			ret *= 37;
			ret += hObj.hashCode();
			ret *= 37;
			ret += p2Set.get(hObj).hashCode();
			i++;
			if (i > range)
				break;
		}

		return ret;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder("{");
		for (HeapObject hObj : p2Set.keySet()) {
			ret.append("(").append(hObj).append(",");
			ret.append(p2Set.get(hObj)).append(") ");
		}
		ret.append("}");
		return ret.toString();
	}

	Iterator<Map.Entry<HeapObject, BoolExpr>> iterator() {
		return p2Set.entrySet().iterator();
	}
}
