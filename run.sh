ant clean
ant jar
java -cp lib/chord.jar -Dchord.work.dir=/home/yufeng/workspace/CFLexamples -Dchord.props.file=chord.properties.example -Dchord.run.analyses=sum-java chord.project.Boot
