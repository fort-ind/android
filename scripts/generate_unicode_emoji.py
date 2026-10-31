import json
import sys
import urllib.request

SOURCE = "https://unicode.org/Public/emoji/15.0/emoji-test.txt"
SKIPPED_GROUPS = {"Component"}
SKIN_TONES = {chr(code) for code in range(0x1F3FB, 0x1F400)}


def main(output_path):
    with urllib.request.urlopen(SOURCE) as response:
        lines = response.read().decode("utf-8").splitlines()
    groups = []
    current = None
    for line in lines:
        if line.startswith("# group:"):
            name = line.split(":", 1)[1].strip()
            current = None if name in SKIPPED_GROUPS else {"group": name, "emoji": []}
            if current is not None:
                groups.append(current)
            continue
        if current is None or not line or line.startswith("#"):
            continue
        codes, _, rest = line.partition(";")
        status, _, description = rest.partition("#")
        if status.strip() != "fully-qualified":
            continue
        emoji = "".join(chr(int(code, 16)) for code in codes.split())
        if any(char in SKIN_TONES for char in emoji):
            continue
        name = description.strip().split(" ", 2)[2]
        current["emoji"].append([emoji, name])
    with open(output_path, "w", encoding="utf-8") as output:
        json.dump(groups, output, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1])
