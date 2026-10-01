#!/usr/bin/env python3
"""Independent reference implementation of draw algorithm v1 (docs/draw-spec-v1.md).

Written from the specification, not from the app's Kotlin code, so that agreement on the published test vectors
shows the specification is complete and unambiguous. Standard library only.

    python3 tools/reference-impl/draw_v1.py docs/test-vectors-v1.json
"""

import hashlib
import hmac
import json
import sys


def canonical_list(usernames):
    """Lowercase, sort by Unicode code point (Python compares str by code point), join with newlines."""
    names = sorted(name.lower() for name in usernames)
    text = "\n".join(names)
    return names, hashlib.sha256(text.encode("utf-8")).digest()


class Stream:
    """HMAC-SHA256(seed, entry_list_hash || k as 8-byte big-endian) blocks, read as big-endian uint32 values."""

    def __init__(self, seed, entry_list_hash):
        self.seed = seed
        self.entry_list_hash = entry_list_hash
        self.k = 0
        self.buffer = b""

    def next_uint32(self):
        if len(self.buffer) < 4:
            block = hmac.new(self.seed, self.entry_list_hash + self.k.to_bytes(8, "big"), hashlib.sha256).digest()
            self.buffer += block
            self.k += 1
        value = int.from_bytes(self.buffer[:4], "big")
        self.buffer = self.buffer[4:]
        return value

    def uniform(self, bound):
        limit = 2**32 - (2**32 % bound)
        while True:
            value = self.next_uint32()
            if value < limit:
                return value % bound


def select(seed, usernames, winners, alternates):
    names, entry_list_hash = canonical_list(usernames)
    target = min(winners + alternates, len(set(names)))
    stream = Stream(seed, entry_list_hash)
    pool = list(names)
    picked = []
    i = 0
    while len(picked) < target:
        j = i + stream.uniform(len(pool) - i)
        pool[i], pool[j] = pool[j], pool[i]
        if pool[i] not in picked:
            picked.append(pool[i])
        i += 1
    picks = [
        {"position": n + 1, "username": name, "role": "WINNER" if n < winners else "ALTERNATE"}
        for n, name in enumerate(picked)
    ]
    return entry_list_hash.hex(), picks


def entries_of(vector):
    if "entries" in vector:
        return vector["entries"]
    spec = vector["generatedEntries"]
    width = len(str(spec["count"]))
    return [f"{spec['prefix']}{n:0{width}d}" for n in range(1, spec["count"] + 1)]


def main(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    assert data["algorithm"] == "v1", data["algorithm"]
    failures = 0
    for vector in data["vectors"]:
        seed = bytes.fromhex(vector["seed"])
        commit_ok = hashlib.sha256(seed).hexdigest() == vector["commitHash"]
        list_hash, picks = select(seed, entries_of(vector), vector["winners"], vector["alternates"])
        ok = commit_ok and list_hash == vector["entryListHash"] and picks == vector["picks"]
        print(("OK   " if ok else "FAIL ") + vector["name"])
        failures += 0 if ok else 1
    print(f"{len(data['vectors']) - failures}/{len(data['vectors'])} vectors match")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "docs/test-vectors-v1.json"))
