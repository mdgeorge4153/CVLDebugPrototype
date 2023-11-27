#!/bin/sh

echo starting > output.txt
export JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
tee logs/input.txt | build/install/Debugger/bin/Debugger | tee logs/output.txt

