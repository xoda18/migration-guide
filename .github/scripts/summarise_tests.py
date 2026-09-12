import datetime
import glob
import subprocess
import xml.etree.ElementTree as ElementTree


def recording_start():
    try:
        with open("ffmpeg.start") as handle:
            return float(handle.read().strip())
    except (OSError, ValueError):
        return None


def offset(timestamp, start):
    if start is None or not timestamp:
        return ""
    try:
        began = datetime.datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        return ""
    seconds = int(began.timestamp() - start)
    if seconds < 0:
        return ""
    return f"{seconds // 60:02d}:{seconds % 60:02d}"


start = recording_start()
rows = []
for path in sorted(glob.glob("*/build/test-results/**/*.xml", recursive=True)):
    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError:
        continue
    at = offset(root.get("timestamp"), start)
    for case in root.iter("testcase"):
        broke = case.find("failure") is not None or case.find("error") is not None
        name = (case.get("classname") or "?").rsplit(".", 1)[-1]
        detail = ""
        if broke:
            node = case.find("failure")
            if node is None:
                node = case.find("error")
            detail = (node.get("message") or "").split("\n")[0][:160]
        rows.append((at, name, broke, case.get("time") or "?", detail))

rows.sort()
passed = sum(1 for row in rows if not row[2])

if not rows:
    print("### No scenarios ran")
    print()
    print("The build produced no JUnit results. Treat this as a failure, not a pass.")
else:
    print(f"### {passed} of {len(rows)} scenarios passed")
    print()
    print("| Video at | Scenario | Result | Time | Message |")
    print("|---|---|---|---|---|")
    for at, name, broke, seconds, detail in rows:
        print(f"| {at or '-'} | {name} | {'fail' if broke else 'pass'} | {seconds}s | {detail} |")
    if start is not None:
        print()
        print("Video at is the position in `screen.mp4`, in the recording artifact.")

print()
disk = subprocess.run(["df", "-h", "/"], capture_output=True, text=True).stdout.strip()
print("```")
print(disk)
print("```")
