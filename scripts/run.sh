#!/bin/bash
cur_dir=`pwd`
search_dir=$cur_dir"/pjbench-read-only/dacapo/benchmarks"
result_folder=$cur_dir"/2cfa_1h_downcast"


#prepare for the result folder

if [ -f $result_folder ];
then
   echo "File $result_folder exists."
else
   echo "File $result_folder does not exist."
   mkdir $result_folder
   echo "Making dir :: "$result_folder
fi

#mkdir $result_folder
#echo "Making dir :: "$result_folder


#compile current project
ant clean compile
echo "Finishing compiling jchord !"


#compile each client application
for entry in `ls $search_dir`; do
    work_dir=$search_dir"/"$entry

    #compile target project
    cd $work_dir
    #dir_now=`pwd`
    ant clean compile

    #run target project
    cd $cur_dir
    ant -Dchord.work.dir=$work_dir -Dchord.run.analyses=cspa-kcfa-dlog,cspa-downcast-java run


    #copy final result to specified folder(folder needs to created first)
    outputfile=$work_dir"/chord_output/log.txt"
    #echo "outputfile is :: "$outputfile
    result_target_folder=$result_folder"/"$entry"/"
    mkdir $result_target_folder

    cp $outputfile $result_target_folder
done




