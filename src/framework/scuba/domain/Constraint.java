package framework.scuba.domain;

public abstract class Constraint {

	public Constraint intersect(Constraint other) {
		return null;
	}

	public Constraint union(Constraint other) {
		return null;
	}

	@Override
	public String toString() {
		return null;
	}
}
