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
    # 20190817T1454-1cc19b18-attempts.tex
    OUTPUT = re.sub(r"\.csv$", r"-attempts.tex", FILE)

# Values for variant, t, a to show.
# The TikZ template below is hard-coded for these values, so this cannot change.
VTAS = [ # ordered
    ("original",  1, None),
    ("original",  2, None),
    ("original",  4, None),
    ("original",  8, None),
    ("original", 16, None),
    ("pbfs",      1,    1),
    ("pbfs",      1,    2),
    ("pbfs",      1,    4),
    ("pbfs",      1,    8),
    ("pbfs",      1,   16),
    ("pbfs",      2,    1),
    ("pbfs",      2,    2),
    ("pbfs",      2,    4),
    ("pbfs",      2,    8),
    ("pbfs",      2,   16),
    ("pbfs",      4,    1),
    ("pbfs",      4,    2),
    ("pbfs",      4,    4),
    ("pbfs",      4,    8),
    ("pbfs",      4,   16),
    ("pbfs",      8,    1),
    ("pbfs",      8,    2),
    ("pbfs",      8,    4),
    ("pbfs",      8,    8),
    ("pbfs",      8,   16),
    ("pbfs",     16,    1),
    ("pbfs",     16,    2),
    ("pbfs",     16,    4),
    ("pbfs",     16,    8),
    ("pbfs",     16,   16),
]

TIKZ_FILE_TEMPLATE = r"""
\documentclass[tikz]{standalone}

% fonts from acmart template
\RequirePackage[T1]{fontenc}
\RequirePackage[tt=false, type1=true]{libertine}
\RequirePackage[varqu]{zi4}
\RequirePackage[libertine]{newtxmath}

%\usepackage{calc} % XXX is this needed?
\usepackage{pgfplots}
\usetikzlibrary{positioning}
\usetikzlibrary{pgfplots.colorbrewer}
\usetikzlibrary{arrows.meta}

\definecolor{gridcolor}{cmyk}{0,0,0,0.08}

\newcommand{\midrule}[1]{$\vcenter{\hbox{\rule{#1}{0.4pt}}}$}

\begin{document}

\begin{tikzpicture}

\begin{axis}[
x=17pt,
y=2.3cm,
ymin=1, ymax=2.5,
ytick={1,1.5,2,2.5},
ylabel={Avg.\ attempts/tx},
xmin=0, xmax=$XMAX,
xtick={$XTICKS},
xticklabels={},
tick align=outside,
xmajorgrids,
ymajorgrids,
grid style={gridcolor},
axis x line*=bottom,
axis y line*=left,
axis line style={gridcolor},
every tick/.append style={gridcolor},
separate axis lines=false,
]

\addplot [scatter, only marks, mark size=4pt, point meta=y, colormap/YlGnBu]
table {%
x   y
$DATA
};
\end{axis}

\begin{scope}[every node/.style={anchor=base,inner sep=0pt,outer sep=0pt,minimum height=3ex}]
    \node[anchor=base east] at (-5pt,  -4ex) {Version:};
    \node[anchor=base east] at (-5pt,  -8ex) {t:};
    \node[anchor=base east] at (-5pt, -12ex) {p:};

    \node[anchor=base west] at (0*17pt, -4ex) {sequential \midrule{$SEQ_RULELENGTHpt}};
    \node[anchor=base west] at (5*17pt, -4ex) {parallel \midrule{$PAR_RULELENGTHpt}};

$T_LABELS

$P_LABELS
\end{scope}

% Annotations
$ANNOTATIONS

\end{tikzpicture}

\end{document}
"""

def parse_file(filename):
    results = defaultdict(list)  # (variant, t, a) -> [attempts]
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
            else:
                a = None
            n_attempts = float(str(n_attempts).strip())
            results[(variant, t, a)].append(n_attempts)

    averages = {}  # (variant, t, a) -> avg_n_attempts
    for k, n_attempts in results.items():
        averages[k] = numpy.mean(n_attempts)

    return averages

def fill_in_template(attempts):
    selected_attempts = [attempts[vta] for vta in VTAS]

    data = ""
    for (i, n_attempts) in enumerate(selected_attempts):
        data += "+{} +{}\n".format(i, n_attempts)

    x_max = str(len(VTAS) - 1)
    x_ticks = ",".join(str(i) for i in range(len(VTAS)))

    n_seq_attempts = sum(1 for (variant, t, a) in VTAS if variant == "original")
    x_label_seq_rulelength = str(n_seq_attempts*17 - 50)
    n_par_attempts = sum(1 for (variant, t, a) in VTAS if variant == "pbfs")
    x_label_par_rulelength = str(n_par_attempts*17 - 55)

    t_labels = ""
    for (i, (variant, t, a)) in enumerate(VTAS):
        if i != 0 and t == VTAS[i-1][1]:
            t = ""
        t_labels += "    \\node at ({:2}*17pt, -8ex) {{{:1}}};\n".format(i, t)

    a_labels = ""
    for (i, (variant, t, a)) in enumerate(VTAS):
        if a == None or (i != 0 and a == VTAS[i-1][2]):
            a = ""
        a_labels += "    \\node at ({:2}*17pt, -12ex) {{{}}};\n".format(i, a)

    annotations = r"""
    \node[anchor=mid west] at (4*17pt + 14pt, 2.53cm) {2.10};
    \draw[Stealth-] (4*17pt + 6pt, 2.53cm) -- (4*17pt + 16pt, 2.53cm);

    \node[anchor=south] at (13*17pt, 1.7cm) {\parbox{4cm}{\centering optimal speed-up \\ 1.11}};
    \draw[Stealth-] (13*17pt, 0.35cm + 4pt) -- (13*17pt, 1.7cm);
    """

    return (TIKZ_FILE_TEMPLATE
        .replace("$DATA", data)
        .replace("$XMAX", x_max)
        .replace("$XTICKS", x_ticks)
        .replace("$SEQ_RULELENGTH", x_label_seq_rulelength)
        .replace("$PAR_RULELENGTH", x_label_par_rulelength)
        .replace("$T_LABELS", t_labels)
        .replace("$P_LABELS", a_labels)
        .replace("$ANNOTATIONS", annotations))

attempts = parse_file(FILE)
#print(attempts)
out = fill_in_template(attempts)
with open(OUTPUT, "x") as f:
    f.write(out)
print("This generated the file {}, which must now be compiled using LaTeX.".format(
    OUTPUT))
