import ctypes, os, re, sys
root = r"C:\wpjc\md\go"
out = os.path.join(root, "build", "dll")
fails = []
for m in sorted(os.listdir(os.path.join(root, "dll"))):
    src = os.path.join(root, "dll", m, "main.go")
    if not os.path.exists(src):
        continue
    names = re.findall(r"^//export ([A-Za-z0-9_]+)", open(src, encoding="utf-8").read(), re.M)
    try:
        lib = ctypes.CDLL(os.path.join(out, m + ".dll"))
    except Exception as e:
        fails.append((m, "load", str(e)))
        continue
    for n in names:
        try:
            getattr(lib, n)
        except AttributeError:
            fails.append((m, n, "missing"))
    print(f"{m}: {len(names)} exports checked")
print("FAILS", fails if fails else "none")
sys.exit(1 if fails else 0)
