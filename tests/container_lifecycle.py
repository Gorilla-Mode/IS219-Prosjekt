"""Exercise the actual Compose setup using disposable sources and volumes."""

import http.cookiejar
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def output(*args):
    return run(*args, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()


def eventually(description, predicate, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if predicate():
                print(f"PASS: {description}", flush=True)
                return
        except (OSError, urllib.error.URLError, subprocess.CalledProcessError, KeyError, IndexError):
            pass
        time.sleep(1)
    raise AssertionError(f"Timed out: {description}")


def browser():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def page(client, base, path):
    with client.open(base + path, timeout=5) as response:
        return response.read().decode()


def submit(client, base, path, fields):
    html = page(client, base, path)
    token = re.search(r'name="_csrf"[^>]*value="([^"]+)"', html).group(1)
    data = urllib.parse.urlencode(dict(fields, _csrf=token)).encode()
    with client.open(base + path, data=data, timeout=10) as response:
        return response.geturl(), response.read().decode()


def main():
    project = "ugc-test-" + uuid.uuid4().hex[:12]
    schema_volume = project + "-schema"
    seed_container = project + "-seed"
    watch = None
    with tempfile.TemporaryDirectory(prefix="ugc-lifecycle-") as temporary:
        root = Path(temporary) / "project"
        shutil.copytree("/workspace", root, ignore=shutil.ignore_patterns("build", ".gradle", ".git", ".kotlin"))
        compose = ["docker", "compose", "--project-name", project, "--project-directory", str(root), "-f", str(root / "compose.fixture.json")]
        # Docker runs on the host: runner-local files cannot be host bind mounts.
        # Keep the real Compose settings, changing only test isolation and that mount.
        config = json.loads(output("docker", "compose", "-f", str(root / "compose.yaml"), "config", "--format", "json"))
        config["name"] = project
        config["networks"]["default"]["name"] = project + "-default"
        config["volumes"]["pgdata"]["name"] = project + "-pgdata"
        config["volumes"]["schema"] = {"name": schema_volume, "external": True}
        for mount in config["services"]["db"]["volumes"]:
            if mount["target"] == "/docker-entrypoint-initdb.d":
                mount.clear()
                mount.update(type="volume", source="schema", target="/docker-entrypoint-initdb.d", read_only=True)
        config["services"]["app"]["ports"] = [{"target": 8080, "published": "0", "protocol": "tcp"}]
        config["services"]["app"]["environment"]["SPRING_PROFILES_ACTIVE"] = "dev"
        (root / "compose.fixture.json").write_text(json.dumps(config))
        log_path = Path(temporary) / "watch.log"

        def compose_output(*args):
            return output(*compose, *args)

        def app_id():
            return compose_output("ps", "-q", "app")

        def base_url():
            inspected = json.loads(output("docker", "inspect", app_id()))[0]
            port = inspected["NetworkSettings"]["Ports"]["8080/tcp"][0]["HostPort"]
            host = os.environ.get("TESTCONTAINERS_HOST_OVERRIDE", "host.docker.internal")
            return f"http://{host}:{port}"

        def login_page_contains(text):
            return text in page(browser(), base_url(), "/login")

        try:
            run("docker", "volume", "create", schema_volume, stdout=subprocess.DEVNULL)
            run("docker", "create", "--name", seed_container, "-v", schema_volume + ":/schema", config["services"]["db"]["image"], "true", stdout=subprocess.DEVNULL)
            run("docker", "cp", str(root / "database/init/01-schema.sql"), seed_container + ":/schema/01-schema.sql")
            run("docker", "rm", seed_container, stdout=subprocess.DEVNULL)
            run(*compose, "up", "--build", "-d", stdout=subprocess.DEVNULL)
            eventually("fresh Compose startup initializes schema without Ollama", lambda: login_page_contains("Log in"))
            assert compose_output("exec", "-T", "db", "psql", "-U", "ugc", "-d", "ugc", "-tAc", "SELECT count(*) FROM app_users") == "0"
            assert compose_output("ps", "--status", "running", "--services").splitlines() == ["app", "db"]
            client = browser()
            url, _ = submit(client, base_url(), "/register", {
                "displayName": "Persistent User", "email": "Persist@Example.com",
                "password": "persistent123", "passwordConfirmation": "persistent123",
            })
            assert url.endswith("/login?registered")
            run(*compose, "down", stdout=subprocess.DEVNULL)
            run(*compose, "up", "-d", stdout=subprocess.DEVNULL)
            eventually("normal down/up preserves PostgreSQL data", lambda: login_page_contains("Log in"))
            client = browser()
            url, html = submit(client, base_url(), "/login", {"email": " PERSIST@EXAMPLE.COM ", "password": "persistent123"})
            assert url.endswith("/") and "Persistent User" in html
            print("PASS: registered account can log in after application and database restart", flush=True)

            with log_path.open("w") as log:
                watch = subprocess.Popen([*compose, "watch", "--no-up"], stdout=log, stderr=subprocess.STDOUT, text=True)
                eventually("Compose Watch starts", lambda: "Watch enabled" in log_path.read_text() or "Watch configuration" in log_path.read_text())
                old_id = app_id()
                template = root / "src/main/resources/templates/login.html"
                template.write_text(template.read_text().replace("<h1>Log in</h1>", "<h1>Template reload verified</h1>"))
                eventually("template sync is visible on browser refresh", lambda: login_page_contains("Template reload verified"))
                assert app_id() == old_id, "Template synchronization must not restart the app"

                kotlin = root / "src/main/kotlin/no/olbrygging/ugc/account/controller/AccountController.kt"
                original = kotlin.read_text()
                kotlin.write_text(original.replace('fun login(): String = "login"', 'fun login(model: Model): String { model.addAttribute("reloadMarker", "Kotlin reload verified"); return "login" }'))
                template.write_text(template.read_text().replace("</main>", '<p th:text="${reloadMarker}"></p></main>'))
                eventually("Kotlin edit rebuilds and restarts the application", lambda: app_id() != old_id and login_page_contains("Kotlin reload verified"), timeout=360)

                previous_log_length = len(log_path.read_text())
                kotlin.write_text(kotlin.read_text() + "\nthis is an intentional compile error\n")
                eventually("watch reports an intentional compile error", lambda: "Compilation error" in log_path.read_text()[previous_log_length:] or "Execution failed for task ':compileKotlin'" in log_path.read_text()[previous_log_length:], timeout=240)
                previous_id = app_id()
                kotlin.write_text(original.replace('fun login(): String = "login"', 'fun login(model: Model): String { model.addAttribute("reloadMarker", "Compile recovery verified"); return "login" }'))
                eventually("corrected compile error recovers through Compose Watch", lambda: app_id() != previous_id and login_page_contains("Compile recovery verified"), timeout=360)

                previous_id = app_id()
                build = root / "build.gradle.kts"
                build.write_text(build.read_text() + "\n// Verify Compose Watch rebuilds build-file changes.\n")
                eventually("Gradle build edit rebuilds and restarts the application", lambda: app_id() != previous_id and login_page_contains("Compile recovery verified"), timeout=360)
            print("PASS: all container lifecycle and reload checks", flush=True)
        except Exception:
            # Application logs may contain submitted values, so keep diagnostics to
            # build/watch output and container state, never HTTP request dumps.
            if log_path.exists():
                print(log_path.read_text()[-12000:], flush=True)
            subprocess.run([*compose, "ps", "-a"], check=False)
            raise
        finally:
            if watch is not None:
                watch.terminate()
                try:
                    watch.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    watch.kill()
                    watch.wait()
            subprocess.run([*compose, "down", "--volumes", "--rmi", "local"], check=False, stdout=subprocess.DEVNULL)
            subprocess.run(["docker", "rm", "-f", seed_container], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run(["docker", "volume", "rm", schema_volume], check=False, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
