import sys
import re
from collections import defaultdict
from pprint import pprint

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20190817T1454-1cc19b18-medians.csv"

def parse_file(filename):
    times = {}  # (variant, t, a) -> time
    with open(filename, "r", encoding="utf-8") as file:
        for line in file:
            if line.strip() == "":
                continue
            try:
                variant, t, a, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            t = int(t)
            if a != "None":
                a = int(a)
            time = float(str(time).strip())
            times[(variant, t, a)] = time

    speedups = {}  # (variant, t, a) -> speedup
    base_time = times[("original", 1, "None")]
    for (variant, t, a) in times.keys():
        speedups[(variant, t, a)] = base_time / times[(variant, t, a)]

    return speedups

def calculate_max_speedups(speedups):
    max_speedup_original = 0
    max_speedup_original_key = None
    max_speedup_pbfs = 0
    max_speedup_pbfs_key = None
    for (key, speedup) in speedups.items():
        if key[0] == "original":
            if speedup > max_speedup_original:
                max_speedup_original = speedup
                max_speedup_original_key = key
        if key[0] == "pbfs":
            if speedup > max_speedup_pbfs:
                max_speedup_pbfs = speedup
                max_speedup_pbfs_key = key
    return (max_speedup_original, max_speedup_original_key, \
            max_speedup_pbfs, max_speedup_pbfs_key)

speedups = parse_file(FILE)
(max_speedup_original, max_speedup_original_key, \
 max_speedup_pbfs, max_speedup_pbfs_key) = calculate_max_speedups(speedups)

# pprint(speedups)

print("original: max speedup = {:.3} for {}\npbfs: max speedup = {:.3} for {}".format(
    max_speedup_original, max_speedup_original_key, \
    max_speedup_pbfs, max_speedup_pbfs_key))
