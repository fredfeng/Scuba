ant clean
ant jar
java -cp lib/chord.jar -Dchord.work.dir=/home/yufeng/research/benchmark/pjbench-read-only/java_grande/raytracer/ -Dchord.run.analyses=cspa-kcfa-dlog,prune-dlog,vv-refine-java,cspa-query-resolve-dlog,cspa-downcast-java,sum-java chord.project.Boot
