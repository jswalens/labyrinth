import sys
import re
from collections import defaultdict
from pprint import pprint

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20190817T1454-1cc19b18-medians.csv"

if len(sys.argv) >= 3:
    OUTPUT = sys.argv[2]
else:
    # E.g. 20190817T1454-1cc19b18-medians.csv to
    # 20190817T1454-1cc19b18-speedup.csv
    OUTPUT = re.sub(r"-medians\.csv$", r"-speedups.csv", FILE)

def parse_file(filename):
    times = {}  # (variant, t, a) -> [25, 50, 75]
    with open(filename, "r", encoding="utf-8") as file:
        file.readline()  # skip header
        for line in file:
            if line.strip() == "":
                continue
            try:
                variant, t, a, first, median, third = line.split(",")
                third = third.strip()  # third is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n{}".format(line))
                continue
            if a != "None":
                a = int(a)
            times[(variant, int(t), a)] = {
                25: float(first),
                50: float(median),
                75: float(third),
            }

    speedups = {}  # (variant, t, a) -> [25, 50, 75]
    base = times[("original", 1, "None")][50]
    def speedup(t):
        return base / t

    for (variant, t, a) in times.keys():
        speedups[(variant, t, a)] = {
            25: speedup(times[(variant, t, a)][75]),
            50: speedup(times[(variant, t, a)][50]),
            75: speedup(times[(variant, t, a)][25]),
        }
        # Watch out: higher time is lower speedup
        # => first quartile in time is third quartile in speedup

    return speedups

def calculate_max_speedups(speedups):
    max_speedup_original = 0
    max_speedup_original_key = None
    max_speedup_pbfs = 0
    max_speedup_pbfs_key = None
    for (key, quartiles) in speedups.items():
        speedup = quartiles[50]
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

def output(speedups):
    out = "variant,t,a,25,median,75\n"
    for k in sorted(speedups.keys()):
        out += "{},{},{},{},{},{}\n".format(k[0], k[1], k[2], speedups[k][25],
            speedups[k][50], speedups[k][75])
    return out

speedups = parse_file(FILE)
(max_speedup_original, max_speedup_original_key, \
 max_speedup_pbfs, max_speedup_pbfs_key) = calculate_max_speedups(speedups)

# pprint(speedups)

print("original: max speedup = {:.3} for {}\npbfs: max speedup = {:.3} for {}".format(
    max_speedup_original, max_speedup_original_key, \
    max_speedup_pbfs, max_speedup_pbfs_key))

out = output(speedups)
with open(OUTPUT, "x") as f:
    f.write(out)
