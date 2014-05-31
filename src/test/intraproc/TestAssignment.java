package test.intraproc;

/**
 * Original test case from the paper, figure 1.
 * @author yufeng
 *
 */
public class TestAssignment {
	
	X x; 
	X y;
	
	class X {
		Z f; 
		Z g;
		
		void bar(Z z) {
			this.f = z;
		}
	}
	
	class Y extends X {
		
		void bar(Z z) {
			this.g = z;
		}
	}
	
	class Z {
		
	}
	
	void foo(Z a) {
		x.bar(a);
		y.bar(a);
	}
	
	void a1() {
		y = new Y();
		x = y;
		Z z = new Z();
		foo(z);
	}
	
	void a2() {
		y = new X();
		x = y;
		Z z = new Z();
		foo(z);
	}
}
