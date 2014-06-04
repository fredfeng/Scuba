package test.intraproc;

//tricky example from xinyu.
public class TestRule20 {
	public static void main(String[] args) {
	
	
	}

	class X {
		F f;
	}
	
	class Y extends X {

	}
	
	class Z extends X {
		F f;
	}
	
	class F {
		
	}

	void foo() {
		X x = new Y();
		x.f = new F();
		F f0 = x.f;

		Y y = (Y)x;
		F f = y.f;
		
		X z = new Z();
		z.f = new F();
		F f1 = z.f;

		Z z1 = (Z)z;
		F f2 = z1.f;
		
		X x2 = z1;
		F f3 = x2.f;
		
		
	}
}
