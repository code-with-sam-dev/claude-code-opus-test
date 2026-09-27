"""True when a Go Store.List body passes its currency argument to SQL only as a
bind parameter: never inside Sprintf and never string-concatenated. String
literal contents are removed first, so a column called currency is not a hit."""
import re, sys

def ok(body: str) -> bool:
    m = re.search(r"func \(s \*Store\) List\(\s*\w+ [\w.]+,\s*(\w+) string", body)
    if not m or "$1" not in body:
        return False
    v = m.group(1)
    code = re.sub(r'`[^`]*`', '``', body, flags=re.S)
    code = re.sub(r'"(\\.|[^"\\])*"', '""', code)
    code = re.sub(r"//[^\n]*", "", code)
    for call in re.finditer(r"Sprintf\(", code):
        depth, i = 1, call.end()
        while i < len(code) and depth:
            depth += {"(": 1, ")": -1}.get(code[i], 0); i += 1
        if re.search(rf"\b{v}\b", code[call.end():i]):
            return False
    return not re.search(rf"\+\s*{v}\b|\b{v}\s*\+", code)

if __name__ == "__main__":
    print("true" if ok(sys.argv[1]) else "false")
