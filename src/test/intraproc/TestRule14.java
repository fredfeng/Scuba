package test.intraproc;

public class TestRule14 {
	public static void main(String[] args) {

	}

	static void foo(Y a) {
		Y y1 = a;
		Y y2 = X.y;
		X.y = y1;
	}
}

class X {
	static Y y = new Y();
}

class Y {

}