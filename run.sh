ant clean
ant jar
java -cp lib/chord.jar -Dchord.work.dir=../CFLexamples/ -Dchord.props.file=chord.properties -Dchord.run.analyses=sum-java chord.project.Boot
