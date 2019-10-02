import sys
import re
from collections import defaultdict
import numpy

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20190817T1454-1cc19b18.csv"

if len(sys.argv) >= 3:
    OUTPUT = sys.argv[2]
else:
    # E.g. 20190817T1454-1cc19b18.csv to
    # 20190817T1454-1cc19b18-medians.csv
    OUTPUT = re.sub(r"(\.[^.]+)$", r"-medians\1", FILE)

def parse_file(filename):
    results = defaultdict(list)  # (variant, t, a) -> [time]
    with open(filename, "r", encoding="utf-8") as file:
        file.readline()  # skip header
        for line in file:
            if line.strip() == "":
                continue
            try:
                variant, t, a, i, time, n_attempts = line.split(",")
                # n_attempts is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            t = int(t)
            if a != "None":
                a = int(a)
            time = float(str(time))
            results[(variant, t, a)].append(time)

    quartiles = {}  # (variant, t, a) -> {25: x, 50: y, 75: z}
    for k, times in results.items():
        quartiles[k] = {
            25: numpy.percentile(times, 25),
            50: numpy.median(times),
            75: numpy.percentile(times, 75),
        }

    out = "variant,t,a,25,median,75\n"
    for k in sorted(quartiles.keys()):
        out += "{},{},{},{},{},{}\n".format(k[0], k[1], k[2], quartiles[k][25],
            quartiles[k][50], quartiles[k][75])
    return out

out = parse_file(FILE)
with open(OUTPUT, "x") as f:
    f.write(out)
