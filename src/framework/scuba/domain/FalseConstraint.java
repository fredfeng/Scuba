package framework.scuba.domain;

public class FalseConstraint extends Constraint {
	@Override
	public String toString() {
		return "FALSE";
	}
	
	public Constraint clone() {
		return new FalseConstraint();
	}
}
