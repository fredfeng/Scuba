package framework.scuba.domain;

import joeq.Class.jq_Method;


/**
 * Representing the summary for a method.
 * Now it only contains abstractHeap.
 * @author yufeng
 *
 */
public class Summary {
	
	private jq_Method method;
	private AbstractHeap absHeap;
	
	public Summary(jq_Method meth, AbstractHeap heap) {
		method = meth;
		absHeap = heap;
	}
	
	public AbstractHeap getAbstractHeap() {
		return absHeap;
	}
	
	public jq_Method getMethod() {
		return method;
	}
}
