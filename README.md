# Labyrinth

This is the Labyrinth benchmark, an adapted version of the Labyrinth benchmark of the STAMP benchmark suite [Minh2008] ported to Clojure and extended with the use of transactional futures [Swalens2016].

## Different versions

In different branches of this repository, you can find different implementations of this benchmark. The most important are:

* linked-list-extra-costs (abbreviated to llcc): a translation of the original benchmark that does not use transactional futures.
* parallel-bfs-extra-costs (abbreviated to pbcc): a version that implements a parallel breadth-first search algorithm using transactional futures.

Tags indicate the latest versions, current llcc-2.5 for the first and pbcc-3.2 for the second version. In [Swalens2016], we used llcc-2.3 and pbcc-3.

## How to run

You can run the program using [Leiningen](https://leiningen.org/).

Run the benchmark as follows (all parameters are optional):

    $ lein run -- -i inputs/random-x64-y64-z3-n48.txt -t 8

Parameters:
* `-i`: name of input file.
* `-t`: number of worker threads to use.
* `-a`: number of partitions to create in each transaction (only for pbcc version).
* `-x`, `-y`, `-z`: costs for moving in the x, y, and z direction.
* `-b`: cost for going round bends.
* `-p`: print result.

(Run `lein run -- -h` to get this description and more.)

Running the program prints the given options and the total execution time to the screen.

## License
Licensed under the MIT license, included in the file `LICENSE`.

## References

[Swalens2016]
J. Swalens, J. De Koster, and W. De Meuter. 2016. "Transactional Tasks: Parallelism in Software Transactions". In _Proceedings of the 30th European Conference on Object-Oriented Programming (ECOOP'16)_.

[Minh2008]
C. C. Minh, J. Chung, C. Kozyrakis, and K. Olukotun. 2008. "STAMP: Stanford Transactional Applications for Multi-Processing". In _Proceedings of the IEEE International Symposium on Workload Characterization (IISWC'08)_.

