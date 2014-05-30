package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import framework.scuba.helper.ArgDerivedHelper;

public class AbstractHeap {

	// the method whose heap is represented by this AbstractHeap
	protected jq_Method method;

	// heap is a translation from heap, which is used to dump the
	// topology of the abstract heap
	// THIS IS the main data structure to represent the abstract heap
	// every time we refer to a heap, it means this heap topology
	protected Map<AbstractMemLoc, Set<HeapObject>> heap;

	// heap is a mapping described in Figure 7 of the paper
	// mapping: (\pi, f) --> \theta
	// THIS IS just a helper field used to get the P2Set but still very critical
	protected Map<HeapObject, P2Set> heapObjectsToP2Set;

	// all the abstract memory locations that have been CREATED as instances in
	// the heap
	protected Set<AbstractMemLoc> memLocFactory;

	// protected ArgDerivedHelper argDerivedHelper = new ArgDerivedHelper();

	public AbstractHeap() {
		heap = new HashMap<AbstractMemLoc, Set<HeapObject>>();
		heapObjectsToP2Set = new HashMap<HeapObject, P2Set>();
		memLocFactory = new HashSet<AbstractMemLoc>();
	}

	// field look-up for location
	public P2Set lookup(AbstractMemLoc loc, FieldElem field) {
		if (ArgDerivedHelper.isArgDerived(loc)) {

		}
	}

	// check whether some abstract memory location is contained in the heap
	public boolean hasCreated(AbstractMemLoc loc) {
		return memLocFactory.contains(loc);
	}

	// check whether some abstract memory location is in the heap
	public boolean isInHeap(AbstractMemLoc loc) {
		return heap.containsKey(loc);
	}

	public AccessPath createAbstractMemLoc(AbstractMemLoc base, FieldElem field) {
		if (memLocFactory.contains(o))
		AccessPath ret = new AccessPath(base, field);
		memLocFactory.add(ret);
		return ret;
	}

	public boolean strongUpdate(HeapObject obj, P2Set p2Set) {

		return false;
	}

	public boolean weakUpdate(HeapObject obj, P2Set p2Set) {

		return false;
	}
}
