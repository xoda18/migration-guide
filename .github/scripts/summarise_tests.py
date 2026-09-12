"""Turn the JUnit XML of whichever module this job built into the run summary.

Without this the Actions page shows only a green or red mark, and a build that ran no
tests at all looks exactly like a build where every scenario passed.
"""

import glob
import subprocess
import xml.etree.ElementTree as ElementTree

rows = []
for path in sorted(glob.glob("*/build/test-results/**/*.xml", recursive=True)):
    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError:
        continue
    for case in root.iter("testcase"):
        broke = case.find("failure") is not None or case.find("error") is not None
        name = (case.get("classname") or "?").rsplit(".", 1)[-1]
        detail = ""
        if broke:
            node = case.find("failure")
            if node is None:
                node = case.find("error")
            detail = (node.get("message") or "").split("\n")[0][:160]
        rows.append((name, broke, case.get("time") or "?", detail))

passed = sum(1 for _, broke, _, _ in rows if not broke)
if not rows:
    print("### No scenarios ran")
    print()
    print("The build produced no JUnit results. Treat this as a failure, not a pass.")
else:
    print(f"### {passed} of {len(rows)} scenarios passed")
    print()
    print("| Scenario | Result | Time | Message |")
    print("|---|---|---|---|")
    for name, broke, seconds, detail in rows:
        print(f"| {name} | {'fail' if broke else 'pass'} | {seconds}s | {detail} |")

print()
disk = subprocess.run(["df", "-h", "/"], capture_output=True, text=True).stdout.strip()
print("```")
print(disk)
print("```")
