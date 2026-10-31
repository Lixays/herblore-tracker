#!/usr/bin/env python3
"""Runs during the GitHub build (which has internet) so the finished app works offline.

 * Downloads every OSRS wiki image the app uses and points www/index.html at the local copies.
 * Downloads the Google fonts and points www/index.html at the local copies.

If a download fails, that one item keeps its original web address, so the app still works online.
The copy of index.html in your repository is never changed - only the copy used for the build.
"""
import hashlib
import os
import re
import sys
import time
import urllib.parse
import urllib.request

WWW = "www"
INDEX = os.path.join(WWW, "index.html")
UA = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Chrome/124.0.0.0 Mobile Safari/537.36 HerbTimerBuild/1.0 (personal project)")
IMAGE_RE = re.compile(r"https://oldschool\.runescape\.wiki/images/[^\s'\"<>)]+")
IMAGE_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}


def fetch(url, tries=3):
    """Download a URL and return its bytes (retries a few times)."""
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=30) as response:
                data = response.read()
            if not data:
                raise IOError("empty response")
            return data
        except Exception as error:  # noqa: BLE001 - we just retry and report
            last = error
            time.sleep(1 + attempt)
    raise last


def looks_like_image(data):
    return (data.startswith(b"\x89PNG") or data.startswith(b"\xff\xd8") or data.startswith(b"GIF8")
            or (data.startswith(b"RIFF") and b"WEBP" in data[:16]) or data.lstrip().startswith(b"<svg"))


def local_name(url, default_ext=".png"):
    ext = os.path.splitext(urllib.parse.urlparse(url).path)[1].lower()
    if ext not in IMAGE_EXT:
        ext = default_ext
    return hashlib.sha1(url.encode("utf-8")).hexdigest()[:12] + ext


def bundle_images(html):
    urls = sorted(set(IMAGE_RE.findall(html)), key=len, reverse=True)   # longest first
    out_dir = os.path.join(WWW, "assets", "img")
    os.makedirs(out_dir, exist_ok=True)
    done = 0
    failed = []
    for url in urls:
        name = local_name(url)
        try:
            data = fetch(url)
            if not looks_like_image(data):
                raise ValueError("not an image")
            with open(os.path.join(out_dir, name), "wb") as f:
                f.write(data)
            html = html.replace(url, "assets/img/" + name)
            done += 1
        except Exception as error:  # noqa: BLE001
            failed.append((url, error))
    print("Images bundled: %d of %d" % (done, len(urls)))
    for url, error in failed:
        print("  could not download (kept online link): %s  (%s)" % (url, error))
    return html


def bundle_fonts(html):
    link = re.search(r"<link[^>]*fonts\.googleapis\.com/css2[^>]*>", html)
    if not link:
        print("Fonts: no Google Fonts link found")
        return html
    href = re.search(r'href="([^"]+)"', link.group(0)).group(1).replace("&amp;", "&")
    try:
        css = fetch(href).decode("utf-8")
        blocks = re.findall(r"/\*\s*([\w-]+)\s*\*/\s*(@font-face\s*\{.*?\})", css, re.S)
        if not blocks:                                  # no subset comments: keep everything
            blocks = [("all", b) for b in re.findall(r"@font-face\s*\{.*?\}", css, re.S)]
        keep = [b for b in blocks if b[0] in ("latin", "latin-ext", "all")]
        if not keep:
            raise ValueError("no usable font blocks")

        out_dir = os.path.join(WWW, "assets", "fonts")
        os.makedirs(out_dir, exist_ok=True)
        local_blocks = []
        for _, block in keep:
            def swap(match):
                font_url = match.group(1).strip("'\"")
                name = hashlib.sha1(font_url.encode("utf-8")).hexdigest()[:12] + ".woff2"
                with open(os.path.join(out_dir, name), "wb") as f:
                    f.write(fetch(font_url))
                return "url(%s)" % name
            local_blocks.append(re.sub(r"url\(([^)]+)\)", swap, block))

        with open(os.path.join(out_dir, "fonts.css"), "w", encoding="utf-8") as f:
            f.write("\n".join(local_blocks) + "\n")

        html = re.sub(r"<link[^>]*fonts\.(googleapis|gstatic)\.com[^>]*>\s*", "", html)
        html = html.replace("<style>", '<link rel="stylesheet" href="assets/fonts/fonts.css">\n    <style>', 1)
        print("Fonts bundled: %d font files" % len(local_blocks))
    except Exception as error:  # noqa: BLE001
        print("Fonts: could not download (kept online link): %s" % error)
    return html


def main():
    if not os.path.isfile(INDEX):
        print("offline_assets: %s not found, skipping" % INDEX)
        return
    html = open(INDEX, encoding="utf-8").read()
    html = bundle_images(html)
    html = bundle_fonts(html)
    with open(INDEX, "w", encoding="utf-8") as f:
        f.write(html)


if __name__ == "__main__":
    main()
