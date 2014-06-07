package test.intraproc;

/**
 * Test case for callsite.
 *
 */
public class TestRule22 {
	
    public static void main(String[] args) {
//    	new Driver().run();
		System.out.println("x");
    }
	/**
	 * @param args
	 */
	public void run() {
		// TODO Auto-generated method stub
		int i = 1;
		X x = new X(); // alloc1
		if (i == 1) {
			X x1 = new X();
			x.f = new A(); // alloc2
			A t1 = x.f;
		} else {
			X x2 = new X();
			x.f = new B(); // alloc3
			A t2 = x.f;
		}
	}

	class X {
		A f = new A();
	}

	class A {

	}

	class B extends A {

	}
}
