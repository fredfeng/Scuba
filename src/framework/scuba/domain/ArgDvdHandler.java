package framework.scuba.domain;

public class ArgDvdHandler {

	protected AbsHeap absHeap;

	public void setAbsHeap(AbsHeap absHeap) {
		this.absHeap = absHeap;
	}

	public void markArgDvd() {
		for (AbsMemLoc loc : absHeap.heap) {
			if (loc instanceof ParamElem || loc instanceof StaticFieldElem
					|| loc instanceof RetElem || loc instanceof AccessPathElem) {
				loc.setArgDvd();
			} else if (loc instanceof AllocElem) {
				if (Env.toProp(loc)) {
					loc.setArgDvd();
				} else {
					loc.resetArgDvd();
				}
			} else if (loc instanceof LocalVarElem) {
				loc.resetArgDvd();
			} else {
				assert false : "wrong!";
			}
		}
	}
}
