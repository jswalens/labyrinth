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
    variant_values = set()
    t_values = set()
    a_values = set()
    with open(filename, "r", encoding="utf-8") as file:
        file.readline()  # skip header
        for line in file:
            if line.strip() == "":
                continue
            try:
                variant, t, a, i, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            t = int(t)
            if a != "None":
                a = int(a)
            time = float(str(time).strip())
            results[(variant, t, a)].append(time)
            variant_values.add(variant)
            t_values.add(t)
            a_values.add(a)

    medians = {}  # (variant, t, a) -> median time
    for k, times in results.items():
        medians[k] = numpy.median(times)

    out = ""
    for k in sorted(medians.keys()):
        out += "%s,%s,%s,%s\n" % (k[0], k[1], k[2], medians[k])
    return out

out = parse_file(FILE)
with open(OUTPUT, "x") as f:
    f.write(out)
