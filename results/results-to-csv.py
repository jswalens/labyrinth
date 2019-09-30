import os, os.path
import sys
import re

if len(sys.argv) >= 2:
    DIRECTORY = sys.argv[1]
else:
    DIRECTORY = "20190817T1454-1cc19b18"

if len(sys.argv) >= 3:
    OUTPUT = sys.argv[2]
else:
    OUTPUT = DIRECTORY + ".csv"

INFO_FILE_FORMAT = re.compile(r"""Input: (?P<input>.*)
Parameters: (?P<input_parameters>.*)
Benchmark parameters: (?P<benchmark_parameters>.*)
Revision: (?P<revision>.*)
Clojure version: (?P<clojure_version>.*)
Date: (?P<timestamp>.*)""")

INPUT_FORMAT = re.compile(r".*-x(?P<x>\d+)-y(?P<y>\d+)-z(?P<z>\d+)-n(?P<n>\d+)")

RESULT_FILE_NAME_FORMAT = re.compile(
    r"(.+)-(original|pbfs)-t(\d+)(?:-a(\d+))?-i(\d+).txt")

RESULT_FILE_FORMAT = re.compile(r"""Variant         = :(?P<variant>.*)
Maze dimensions = (?P<x>\d+) x (?P<y>\d+) x (?P<z>\d+)
Paths to route  = (?P<n>\d+)
Paths routed    = (?P<n_routed>\d+)
Elapsed time    = (?P<total_time>[\d.]+) milliseconds
(?P<debugging_info>.*)
Verification passed\.""", flags=re.DOTALL)

def parse_info(dir_name):
    contents = open(os.path.join(dir_name, "info.txt")).read()

    print(contents)

    matches = INFO_FILE_FORMAT.search(contents)
    if matches is None:
        print("Error: info.txt does not match expected output.")
        return False

    expected_directory = matches.group("timestamp") + "-" + matches.group("revision")
    if DIRECTORY != expected_directory:
        print("Warning: expected directory name to be {} (timestamp-revision)"
            "but is {}.".format(expected_directory, DIRECTORY))

    return {
        "input": matches.group("input"),
    }

def print_line(variant, t, a, i, time):
    return "%s,%s,%s,%s,%s\n" % (variant, t, a, i, time)

def parse_results_dir(dir_name, parameters=False):
    out = "variant,t,a,i,time (ms)\n"
    errors = []

    for f_name in os.listdir(dir_name):
        if f_name == "info.txt":
            continue

        match = RESULT_FILE_NAME_FORMAT.search(f_name)
        if match is None:
            errors.append("Warning: ignoring result file {}: wrong file "
                "name.".format(f_name))
            continue
        input, variant, t, a, i = match.groups()
        if parameters and input != parameters["input"]:
            errors.append("Error: input files do not match (info.txt says {} "
                "but result file name starts with {}).".format(
                    parameters["input"], input))

        contents = open(os.path.join(dir_name, f_name)).read()
        matches = RESULT_FILE_FORMAT.search(contents)

        if matches is None:
            errors.append("Error: file {} did not match expected output. "
                "Verify its contents to make sure the verification "
                "passed.".format(f_name))
            continue

        if matches.group("variant") != variant:
            errors.append("Error: in file {}, expected variant to be {} but is "
                "{}.".format(f_name, variant, matches.group("variant")))

        input_matches = INPUT_FORMAT.search(input)
        for p in ["x", "y", "z", "n"]:
            if matches.group(p) != input_matches.group(p):
                errors.append("Error: in file {}, expected {} to be {} but is "
                    "{}.".format(f_name, p, input_matches.group(p),
                        matches.group(p)))

        time = matches.group("total_time")

        out += print_line(variant, t, a, i, time)

    return out, errors

parameters = parse_info(DIRECTORY)
out, errors = parse_results_dir(DIRECTORY, parameters)

with open(OUTPUT, "x") as f:
    f.write(out)

if len(errors) != 0:
    print("\n".join(errors))
