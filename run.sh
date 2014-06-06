ant clean
ant jar
DYLD_LIBRARY_PATH=/Users/yufeng/Documents/workspace/Scuba/lib/  java -cp lib/chord.jar -Dchord.work.dir=/Users/yufeng/Documents/workspace/CFLexamples/ -Dchord.ssa.kind=phi -Dchord.props.file=chord.properties -Dchord.run.analyses=sum-java chord.project.Boot
