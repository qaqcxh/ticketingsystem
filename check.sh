#!/bin/sh

## compile Trace.java and put .class into bin
javac ticketingsystem/Trace.java -d ./bin

result=1

## begin test
for i in $(seq 1 500000); do ## you can change the number of test, default is 50
    echo -n $i
    java -cp bin ticketingsystem/Trace > trace 
    java -jar checker.jar --no-path-info --coach 3 --seat 5 --station 5 < trace
    if [ $? != 0 ]; then
        echo "Test failed!!! see trace file to debug"
        result=0
        break
    fi
done

if [ $result == 1 ]; then
    echo "Test passed!!!"
fi
