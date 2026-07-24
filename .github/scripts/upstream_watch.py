#!/usr/bin/env python3
"""Compare each pinned upstream source against its published latest.

Reports drift by opening or updating one GitHub issue per drifted source. Never updates
data: a pin moves through a reviewed change.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request

LABEL = "upstream-drift"
USER_AGENT = "world-upstream-watch (+https://github.com/shuwariafrica/world)"
TIMEOUT = 60


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.read().decode("utf-8", errors="replace")


def published_version(check: dict) -> str:
    method = check["method"]
    body = fetch(check["url"])
    if method == "text":
        return body.strip()
    if method == "github-release":
        return json.loads(body)["tag_name"]
    if method == "regex":
        match = re.search(check["pattern"], body, re.MULTILINE)
        if match is None:
            raise ValueError(f"pattern {check['pattern']!r} did not match")
        return match.group(1)
    raise ValueError(f"unknown check method {method!r}")


def issue_body(source: dict, latest: str) -> str:
    pinned = source["pinned"]
    return "\n".join(
        [
            f"`{source['name']}` has moved upstream.",
            "",
            f"- Pinned: **{pinned['version']}**",
            f"- Published: **{latest}**",
            f"- Authority: {source['authority']}",
            f"- Content: {source['content']}",
            f"- Cadence: {source['cadence']}",
            f"- Source: {source['check']['url']}",
            "",
            "The watcher reports only. Update the dataset and its pin in "
            "`data/upstream-pins.json` through a reviewed change.",
        ]
    )


def gh(*args: str) -> str:
    result = subprocess.run(
        ["gh", *args], capture_output=True, text=True, check=True
    )
    return result.stdout


def open_issue_number(title: str) -> int | None:
    listing = json.loads(
        gh("issue", "list", "--label", LABEL, "--state", "open", "--json", "number,title", "--limit", "200")
    )
    for issue in listing:
        if issue["title"] == title:
            return issue["number"]
    return None


def report(source: dict, latest: str, dry_run: bool) -> None:
    title = f"upstream: {source['name']} has moved"
    body = issue_body(source, latest)
    if dry_run:
        print(f"--- would report ---\n{title}\n{body}\n")
        return
    existing = open_issue_number(title)
    if existing is None:
        gh("issue", "create", "--title", title, "--body", body, "--label", LABEL)
        print(f"opened issue for {source['name']}")
    else:
        gh("issue", "edit", str(existing), "--body", body)
        print(f"updated issue #{existing} for {source['name']}")


def resolve(source: dict, dry_run: bool) -> None:
    title = f"upstream: {source['name']} has moved"
    if dry_run:
        return
    existing = open_issue_number(title)
    if existing is not None:
        gh("issue", "close", str(existing), "--comment", "Pin now matches the published version.")
        print(f"closed issue #{existing} for {source['name']}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pins", default="data/upstream-pins.json")
    parser.add_argument("--dry-run", action="store_true")
    arguments = parser.parse_args()

    with open(arguments.pins, encoding="utf-8") as handle:
        pins = json.load(handle)

    failures = 0
    for source in pins["sources"]:
        name = source["name"]
        check = source["check"]
        if check["method"] == "manual":
            print(f"{name}: manual review only ({check['url']}), pinned {source['pinned']['version']}")
            continue
        try:
            latest = published_version(check)
        except (urllib.error.URLError, ValueError, KeyError, json.JSONDecodeError) as error:
            # A source that cannot be read is a watcher fault, not upstream drift: say so
            # and carry on rather than reporting a version change that was never observed.
            print(f"{name}: could not determine published version: {error}", file=sys.stderr)
            failures += 1
            continue
        pinned = source["pinned"]["version"]
        if latest == pinned:
            print(f"{name}: current ({pinned})")
            resolve(source, arguments.dry_run)
        else:
            print(f"{name}: DRIFTED {pinned} -> {latest}")
            report(source, latest, arguments.dry_run)

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
