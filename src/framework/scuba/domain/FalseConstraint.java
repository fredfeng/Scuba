package framework.scuba.domain;

public class FalseConstraint extends Constraint {
	
	final String id = "FALSE";

	@Override
	public String toString() {
		return id;
	}
	
	public Constraint clone() {
		return new FalseConstraint();
	}
}
