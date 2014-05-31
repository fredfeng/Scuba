package framework.scuba.domain;

public abstract class Constraint {
	public abstract Constraint clone();
	
	@Override
	public boolean equals(Object other) {
		//equal when both are exactly the same class.
		return (this.getClass().equals(other.getClass()));
	}
}
