package framework.scuba.domain;

public class PrimitiveElemFactory {

	private static PrimitiveElem primitive = new PrimitiveElem();

	public static PrimitiveElem getPrimitiveElem() {
		return primitive;
	}
}
