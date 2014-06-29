ant clean
ant jar
java -cp lib/chord.jar -Dchord.work.dir=/home/yufeng/research/benchmark/pjbench-read-only/dacapo/benchmarks/antlr/ -Dchord.run.analyses=cspa-0cfa-dlog,prune-dlog,cspa-query-resolve-dlog,cspa-downcast-java,sum-java chord.project.Boot
