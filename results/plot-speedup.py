import sys
import os
import re
from collections import defaultdict, OrderedDict

import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
import matplotlib.colors as colors

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20190817T1454-1cc19b18-speedups.csv"

if len(sys.argv) >= 3:
    OUTPUT_BASE = sys.argv[2]
else:
    # E.g. 20190817T1454-1cc19b18-speedups.csv to 20190817T1454-1cc19b18
    OUTPUT_BASE = re.sub(r"-speedups\.csv$", "", FILE)

# To determine this number, run the pbfs variant with profiling enabled (-m)
# and calculate the ratio spent in the step "expand" over the total execution
# time of the program. Ideally, this is the median of 3 runs.
PARALLELIZABLE_PROPORTION = 0.714

def amdahl(t):
    return 1 / ((1 - PARALLELIZABLE_PROPORTION) + PARALLELIZABLE_PROPORTION / t)

# ts to plot
TS = [
    1,
    2,
    4,
    8,
    16,
    #24, - actual optimum but X is close enough
    32,
    64,
]

# (variant, a)s to plot
VAS = [ # ordered
    ("amdahl", 1),
    ("original", 1),
    ("pbfs", 1),
    ("pbfs", 2),
    ("pbfs", 4),
    ("pbfs", 8),
    #("pbfs", 12), - actual optimum but 16 is close enough
    ("pbfs", 16),
    #("pbfs", 32),
    #("pbfs", 64),
]

X_TICKS = [1, 2, 4, 8, 16, 32, 64, 128, 256]

LABELS = {
    ("original", 1): "Sequential search",
    ("pbfs", 1):     "Parallel search, 1 partition",
    ("pbfs", 2):     "Parallel search, 2 partitions",
    ("pbfs", 4):     "Parallel search, 4 partitions",
    ("pbfs", 8):     "Parallel search, 8 partitions",
    ("pbfs", 16):    "Parallel search, 16 partitions",
    ("amdahl", 1):   "Theor. max (Amdahl's law)",
}

# VUB orange = #FF6600 in HSV = 24,1.00,1.00
palette = [
    [ 0, 1.00, 0.20],
    [ 6, 1.00, 0.40],
    [12, 1.00, 0.60],
    [18, 1.00, 0.80],
    [24, 1.00, 1.00],
]
palette = [colors.hsv_to_rgb([c[0] / 360.0, c[1], c[2]]) for c in palette]
COLORS = {
    ("original", 1): "#003399",
    ("pbfs", 1):     palette[0],
    ("pbfs", 2):     palette[1],
    ("pbfs", 4):     palette[2],
    ("pbfs", 8):     palette[3],
    ("pbfs", 16):    palette[4],
    ("amdahl", 1):   "#99999966",
}

def calculate_amdahl(base):
    speedups = OrderedDict()
    for t in X_TICKS:
        s = base * amdahl(t)
        speedups[t] = {"median": s, "errors": [0, 0]}
    return speedups

def parse_file(filename):
    speedups = defaultdict(lambda: defaultdict(dict))
    # (variant, a) -> t -> {"median": x, "errors": [y, z]}
    with open(filename, "r", encoding="utf-8") as file:
        file.readline()  # skip header
        for line in file:
            if line.strip() == "":
                continue
            try:
                variant, t, a, first, median, third = line.split(",")
                third = third.strip()  # third is suffixed with \n
                t = int(t)
            except ValueError:
                print("Error: could not read line:\n%s" % line)
                continue
            if a != "None":
                a = int(a)
            else:
                a = 1
            if (variant, a) not in VAS or t not in TS:
                continue
            speedups[(variant, a)][t] = {
                "median": float(median),
                "errors": [float(median) - float(first),
                           float(third) - float(median)],
            }

    speedups[("amdahl", 1)] = calculate_amdahl(speedups[("pbfs", 1)][1]["median"])

    # (variant, a) -> t -> {"median": x, "errors": [y, z]}
    # but now ordered
    speedups = OrderedDict(
        (va,
            OrderedDict(
                (t, speedups[va][t])
                for t in sorted(speedups[va].keys())),
        )
        for va in VAS)

    return speedups

def draw(speedups):
    # Type 1 fonts
    plt.rcParams["ps.useafm"] = True
    plt.rcParams["pdf.use14corefonts"] = True
    #plt.rcParams["text.usetex"] = True

    sns.set_style("whitegrid", {"grid.color": ".9"})

    ax = plt.axes()
    sns.despine(top=True, right=True, left=True, bottom=True)

    #ax.set_title("Speed-up of different versions", fontsize="x-large")

    ax.set_xlabel(r"Maximal number of threads ($t \times\ p$)", fontsize="large")
    ax.set_xscale("log", basex=2)
    ax.set_xticks(X_TICKS)
    ax.set_xticklabels(X_TICKS)
    ax.set_xlim(0.98, 259)

    ax.set_ylabel("Speed-up", fontsize="large")
    ax.set_ylim(0, 2.5)
    ax.set_yticks([0.0, 0.5, 1.0, 1.5, 2.0, 2.5])

    lines = {}
    for ((variant, a), series) in speedups.items():
        n_threads = [t*a for t in series.keys()]
        medians = [result["median"] for result in series.values()]
        errors = np.transpose([result["errors"] for result in series.values()])
        line = ax.errorbar(x=n_threads, y=medians, yerr=errors,
            color=COLORS[(variant, a)])
        lines[(variant, a)] = line

    ax.legend([lines[va] for va in VAS], [LABELS[va] for va in VAS],
       loc="lower right", prop={"size": "small"})

    arrowprops = {
        "arrowstyle": "->",
        "color": "black",
        "connectionstyle": "angle3,angleA=0,angleB=90",
        "shrinkB": 5,
    }
    ax.annotate(xy=(1, 1.0), s="sequential search\nwith $t = 1$:\ntime = 57.3 s",
        xytext=(2, 80), textcoords="offset points", arrowprops=arrowprops)

    ax.annotate(xy=(128, 2.04), s="optimum for $p = 16$, $t = 8$:\ntime = 28.1 s\nspeed-up = 2.04",
        xytext=(-100, 2), textcoords="offset points", arrowprops=arrowprops)

    ax.annotate(xy=(1, 0.71), s="parallel search with $p = 1$, $t = 1$:\ntime = 80.9 s\nspeed-up = 0.71",
        xytext=(2, -60), textcoords="offset points", arrowprops=arrowprops)

    plt.savefig(OUTPUT_BASE + ".pdf", bbox_inches="tight")
    #plt.show()

speedups = parse_file(FILE)
draw(speedups)
