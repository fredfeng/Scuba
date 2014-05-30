package framework.scuba.domain;

public abstract class AbstractMemLoc {

	// 0 : N.A. 1: is argument derived. -1 : not argument derived
	int argDerived = 0;

	abstract public AbstractMemLoc findRoot();

	public void setArgDerived() {
		this.argDerived = 1;
	}

	public void resetArgDerived() {
		this.argDerived = -1;
	}

	public boolean knownArgDerived() {
		return (this.argDerived != 0);
	}

	public boolean isArgDerived() {
		return (this.argDerived == 1);
	}

}
