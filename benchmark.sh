#!/bin/bash
#
# Benchmarking script
#
# Usage:
# Use --all to run more extensive tests, or --quick to run fewer variations.
# Call this as `JVM_OPTS="..." ./benchmark.sh` to pass arguments to the JVM.

set -ex

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
clj=`grep ":resource-paths" project.clj | sed -n 's/.*"resources\/\(.*\)\.jar".*/\1/p'`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"

: ${INPUT:="random-x64-y64-z3-n32"}
: ${PARAMETERS:=""}

if [ "$1" == "--all" ]; then
    benchmark_parameters="all"
    is=$(seq 1 30)
    ts="1 2 3 4 6 8 12 16 24 32 48 64"
    as="1 2 3 4 6 8 12 16 24 32 48 64"
elif [ "$1" == "--quick" ]; then
    benchmark_parameters="quick"
    is=$(seq 1 3)
    ts="1 2 4 8 16 32 64"
    as="1 2 4 8 16 32"
else
    benchmark_parameters="normal"
    is=$(seq 1 5)
    ts="1 2 4 8 16 32 64"
    as="1 2 4 8 16 32 64"
fi

info="Input: $INPUT
Parameters: $PARAMETERS
Benchmark parameters: $benchmark_parameters
Revision: $rev
Clojure version: $clj
Date: $date"
echo "$info"

echo "Installing/checking lein..."
./lein version
echo "lein installed"

echo "Making uberjar"
./lein uberjar
echo "Uberjar made"

echo "Benchmarking..."

mkdir -p "$result_path"
echo "$info" > "$result_path/info.txt"

for i in $is
do
    # ORIGINAL VERSION
    version="original"
    for t in $ts
    do
        ./lein run -- -v $version -i "$pwd/inputs/$INPUT.txt" -t $t $PARAMETERS > "$result_path/$INPUT-$version-t$t-i$i.txt"
    done

    # PBFS VERSION
    version="pbfs"
    for t in $ts
    do
        for a in $as
        do
            ./lein run -- -v $version -i "$pwd/inputs/$INPUT.txt" -t $t -a $a $PARAMETERS > "$result_path/$INPUT-$version-t$t-a$a-i$i.txt"
        done
    done
done

echo "Benchmark done"
