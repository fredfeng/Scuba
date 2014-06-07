package test.interproc;

//simple example from the paper.
public class TestInter1 {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new TestInter1().run();
	}
	
	void run() {
		A a = new A();
		a.a1();
		a.a2();
	}
	
	class A {
		X x; X y;
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
			x = new X();
			y = x;
			Z z = new Z();
			foo(z);
		}
	}
	
	class Z {
	}
	
	class X {
		Z f; Z g;
		void bar(Z z){
			this.f = z;
		}
	}
	
	class Y extends X {
		
		void bar(Z z){
			this.g = z;
		}
	}

}
