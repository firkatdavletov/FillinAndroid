#!/usr/bin/env python3
"""Run T11's isolated commit checkpoint, kill the app, and verify recovery in a new PID.

Install debug and androidTest APKs first. This stops Fillin; financial test data uses
separate Room files and never calls the configured backend. Logs are private files.
"""
import argparse
from pathlib import Path
import os
import subprocess
import tempfile
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="ADB device serial")
    parser.add_argument("--adb", default="adb", help="Path to adb")
    args = parser.parse_args()
    base = [args.adb, "-s", args.device]
    run_id = str(uuid.uuid4())
    logs = Path(tempfile.mkdtemp(prefix="fillin-t11-process-"))
    logs.chmod(0o700)
    (logs / "run-id").write_text(run_id)
    case = "com.firkat.fillin.feature.budgetconnection.BudgetProcessRecoveryTest"
    runner = "com.firkat.fillin.test/com.firkat.fillin.FillinTestRunner"

    def command(phase):
        return base + ["shell", "am", "instrument", "-w", "-e", "class", case,
                       "-e", "t11Phase", phase, "-e", "t11Run", run_id, runner]

    # Restrict permissions for every file created below, including captured logs.
    os.umask(0o077)
    with (logs / "prepare.txt").open("w") as output:
        process = subprocess.Popen(command("prepare"), stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                probe = subprocess.run(base + ["shell", "run-as", "com.firkat.fillin", "test", "-s",
                                       "files/t11-process-" + run_id + ".json"],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
                if probe.returncode == 0:
                    break
                if process.poll() is not None:
                    raise RuntimeError("Prepare ended before the commit checkpoint")
                time.sleep(0.25)
            else:
                raise TimeoutError("Commit checkpoint did not arrive within 60 seconds")
            print("Commit checkpoint persisted; stopping Fillin's real process.", flush=True)
        finally:
            subprocess.run(base + ["shell", "am", "force-stop", "com.firkat.fillin"],
                           check=True, capture_output=True, timeout=15)
            process.wait(timeout=15)

    with (logs / "recover.txt").open("w") as output:
        subprocess.run(command("recover"), stdout=output, stderr=subprocess.STDOUT,
                       check=True, timeout=120)
    result = (logs / "recover.txt").read_text()
    if "OK (1 test)" not in result or "FAILURES" in result:
        raise RuntimeError("Recovery failed; inspect " + str(logs))
    print("Verified new PID, original request/payload, one ledger entry, and cleared running flag.")
    print("Private logs: " + str(logs))


if __name__ == "__main__":
    main()
