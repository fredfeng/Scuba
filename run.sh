ant clean
ant jar
java -cp lib/chord.jar -Dchord.work.dir=/home/yufeng/workspace/CFLexamples/ -Dchord.ssa.kind=phi -Dchord.props.file=chord.properties -Dchord.run.analyses=sum-java chord.project.Boot
