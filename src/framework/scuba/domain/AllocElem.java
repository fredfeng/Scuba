package framework.scuba.domain;

public class AllocElem extends HeapObject {

	protected AllocSite allocSite;

	protected Context context;

	public AllocElem(AllocSite allocSite, Context context) {
		this.allocSite = allocSite;
		this.context = context;
	}

	public AbstractMemLoc findRoot() {
		return this;
	}

	public void appendContextFront(ProgramPoint point) {
		this.context.appendFront(point);
	}

	public void appendContextEnd(ProgramPoint point) {
		this.context.appendEnd(point);
	}

	@Override
	public String toString() {
		return allocSite + " || " + context;
	}

}
