import logging
import os
import random
import re
import shutil
import tempfile
from urllib.parse import urlencode, urlparse

from flask import Flask, jsonify, request, send_file, send_from_directory
from werkzeug.exceptions import HTTPException, NotFound
from yt_dlp import YoutubeDL
from yt_dlp.utils import DownloadError


app = Flask(__name__)
app.logger.setLevel(logging.INFO)

MAX_MP4_QUALITIES = 5
DOWNLOAD_TIMEOUT = 60
MAX_YTDLP_ATTEMPTS = 3
PUBLIC_FILES = {
    "404.html",
    "ads.txt",
    "android-chrome-192x192.png",
    "android-chrome-512x512.png",
    "apple-touch-icon.png",
    "CNAME",
    "dmca.html",
    "favicon-16x16.png",
    "favicon-32x32.png",
    "favicon.ico",
    "favicon.png",
    "google671d3321b9dedcae.html",
    "index.html",
    "logo.png",
    "privacy.html",
    "robots.txt",
    "site.webmanifest",
    "sitemap.xml",
}

# Add real proxies here, or set YTDLP_PROXIES as a comma-separated list.
# Example: YTDLP_PROXIES="http://user:pass@host:port,http://host2:port"
proxies = [
    # "http://host:port",
    # "http://host2:port",
]

# YouTube often blocks anonymous datacenter traffic. Configure one of these:
# YTDLP_COOKIES_FILE="C:\path\to\cookies.txt"
# cookies.txt in this project folder
# YTDLP_COOKIES_BROWSER="chrome" or "edge" or "firefox"
# YTDLP_COOKIES_BROWSERS="chrome,edge,firefox"  # optional fallback list
# YTDLP_COOKIES_BROWSER_PROFILE="Default"  # optional


class ApiError(Exception):
    def __init__(self, message, status_code=400):
        super().__init__(message)
        self.message = message
        self.status_code = status_code


@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = os.getenv("CORS_ORIGIN", "*")
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    response.headers["Access-Control-Expose-Headers"] = "Content-Disposition"
    return response


def validate_url(raw_url):
    url = str(raw_url or "").strip()

    if not url:
        raise ApiError("url is required", 400)

    if re.search(r"[\s\"'`<>\\{}|^]", url):
        raise ApiError("url contains invalid characters", 400)

    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ApiError("url must be a valid http or https URL", 400)

    return url


def detect_platform(video_url):
    host = urlparse(video_url).netloc.lower()
    host = host[4:] if host.startswith("www.") else host

    if host == "youtu.be" or host.endswith("youtube.com"):
        return "youtube"
    if host.endswith("tiktok.com"):
        return "tiktok"
    if host == "x.com" or host.endswith("twitter.com"):
        return "x/twitter"
    if host.endswith("instagram.com"):
        return "instagram"

    return "unknown"


def ytdlp_base_options():
    return {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "socket_timeout": DOWNLOAD_TIMEOUT,
        "retries": 2,
    }


def configured_proxies():
    env_proxies = [
        item.strip()
        for item in os.getenv("YTDLP_PROXIES", "").split(",")
        if item.strip()
    ]
    unique_proxies = []
    seen = set()

    for proxy in [*env_proxies, *proxies]:
        if proxy not in seen:
            unique_proxies.append(proxy)
            seen.add(proxy)

    return unique_proxies


def proxy_attempts():
    pool = configured_proxies()

    if not pool:
        return [None]

    attempts = []
    previous_proxy = None

    while len(attempts) < MAX_YTDLP_ATTEMPTS:
        available = [proxy for proxy in pool if proxy != previous_proxy] or pool
        proxy = random.choice(available)
        attempts.append(proxy)
        previous_proxy = proxy

    return attempts


def add_proxy_option(options, proxy):
    if proxy:
        options["proxy"] = proxy
    return options


def configured_cookie_sources():
    cookie_files = [
        item.strip()
        for item in os.getenv("YTDLP_COOKIES_FILE", "").split(",")
        if item.strip()
    ]
    default_cookie_file = os.path.join(app.root_path, "cookies.txt")
    if os.path.isfile(default_cookie_file):
        cookie_files.append(default_cookie_file)

    browser_values = [
        os.getenv("YTDLP_COOKIES_BROWSER", ""),
        os.getenv("YTDLP_COOKIES_BROWSERS", ""),
    ]
    browsers = [
        item.strip().lower()
        for value in browser_values
        for item in value.split(",")
        if item.strip()
    ]
    browser_profile = os.getenv("YTDLP_COOKIES_BROWSER_PROFILE", "").strip()
    sources = []
    seen = set()

    for cookie_file in cookie_files:
        if not os.path.isfile(cookie_file):
            raise ApiError(
                f"YTDLP_COOKIES_FILE does not exist: {cookie_file}",
                500,
            )

        key = ("file", cookie_file)
        if key not in seen:
            sources.append(
                {
                    "type": "file",
                    "value": cookie_file,
                }
            )
            seen.add(key)

    for browser in browsers:
        key = ("browser", browser, browser_profile or None)
        if key not in seen:
            sources.append(
                {
                    "type": "browser",
                    "value": browser,
                    "profile": browser_profile or None,
                }
            )
            seen.add(key)

    return sources


def configured_cookie_source():
    sources = configured_cookie_sources()
    return sources[0] if sources else None


def cookie_attempts():
    return configured_cookie_sources() or [None]


def cookie_source_label(source):
    if not source:
        return "none"

    if source["type"] == "file":
        return f"file:{os.path.basename(source['value'])}"

    profile = source.get("profile")
    return f"browser:{source['value']}{':' + profile if profile else ''}"


def add_cookie_options(options, source):
    if not source:
        return options

    if source["type"] == "file":
        options["cookiefile"] = source["value"]
        return options

    options["cookiesfrombrowser"] = (
        source["value"],
        source.get("profile"),
        None,
        None,
    )
    return options


def build_ytdlp_options(options, proxy, cookie_source):
    return add_cookie_options(add_proxy_option(options, proxy), cookie_source)


def is_youtube_bot_error(error):
    message = str(error).lower()
    return "sign in to confirm" in message and "not a bot" in message


def is_cookie_access_error(error):
    message = str(error).lower()
    return (
        "could not copy" in message and "cookie" in message
    ) or (
        "could not decrypt" in message and "cookie" in message
    ) or (
        "could not find" in message and "cookies database" in message
    ) or (
        "cookie database" in message
    )


def clean_error_message(error):
    message = re.sub(r"\x1b\[[0-9;]*m", "", str(error))
    return re.sub(r"\s+", " ", message).strip()


def run_ytdlp_with_retries(operation_name, worker):
    proxies_to_try = proxy_attempts()
    cookies_to_try = cookie_attempts()
    attempts = [
        (proxy, cookie_source)
        for proxy in proxies_to_try
        for cookie_source in cookies_to_try
    ][:MAX_YTDLP_ATTEMPTS]
    last_error = None

    for index, (proxy, cookie_source) in enumerate(attempts, start=1):
        proxy_label = proxy or "direct connection"
        cookie_label = cookie_source_label(cookie_source)
        app.logger.info(
            "yt-dlp %s attempt %s/%s using proxy: %s cookies: %s",
            operation_name,
            index,
            len(attempts),
            proxy_label,
            cookie_label,
        )

        try:
            return worker(proxy, cookie_source)
        except DownloadError as error:
            last_error = error
            app.logger.warning(
                "yt-dlp %s failed with proxy %s cookies %s: %s",
                operation_name,
                proxy_label,
                cookie_label,
                clean_error_message(error),
            )

    if last_error:
        if is_cookie_access_error(last_error):
            raise ApiError(
                "yt-dlp could not read browser cookies. Close the browser and try "
                "again, use another logged-in browser with YTDLP_COOKIES_BROWSERS, "
                "or export YouTube cookies to cookies.txt and set YTDLP_COOKIES_FILE.",
                502,
            )

        if is_youtube_bot_error(last_error) and not configured_cookie_sources():
            raise ApiError(
                "YouTube is requiring authentication cookies. Configure "
                "YTDLP_COOKIES_FILE with an exported cookies.txt file, or set "
                "YTDLP_COOKIES_BROWSER=chrome/edge/firefox on the local machine.",
                502,
            )

        if is_youtube_bot_error(last_error):
            raise ApiError(
                "YouTube is still rejecting the request even with the configured "
                "cookies. Refresh/export new YouTube cookies from a logged-in "
                "browser and try again.",
                502,
            )

    if configured_proxies():
        raise ApiError(
            f"yt-dlp {operation_name} failed after {len(attempts)} attempts with proxy rotation: {clean_error_message(last_error)}",
            502,
        )

    raise ApiError(
        f"yt-dlp {operation_name} failed without configured proxies: {clean_error_message(last_error)}. "
        "Add real proxies to the proxies list or set YTDLP_PROXIES.",
        502,
    )


def extract_metadata(video_url):
    def worker(proxy, cookie_source):
        options = build_ytdlp_options(
            {
                **ytdlp_base_options(),
                "skip_download": True,
            },
            proxy,
            cookie_source,
        )

        with YoutubeDL(options) as ydl:
            return ydl.extract_info(video_url, download=False)

    return run_ytdlp_with_retries("metadata", worker)


def best_thumbnail(info):
    if info.get("thumbnail"):
        return info["thumbnail"]

    thumbnails = info.get("thumbnails") or []
    if not thumbnails:
        return None

    sorted_thumbnails = sorted(
        thumbnails,
        key=lambda item: (item.get("width") or 0) * (item.get("height") or 0),
        reverse=True,
    )
    return sorted_thumbnails[0].get("url")


def mp4_formats(info):
    formats = info.get("formats") or []
    selected_by_height = {}

    for item in formats:
        height = item.get("height")
        if not height:
            continue

        if item.get("ext") != "mp4":
            continue

        if item.get("vcodec") in (None, "none"):
            continue

        current = selected_by_height.get(height)
        current_tbr = current.get("tbr") or 0 if current else -1
        item_tbr = item.get("tbr") or 0

        if current is None or item_tbr > current_tbr:
            selected_by_height[height] = item

    return sorted(
        selected_by_height.values(),
        key=lambda item: item.get("height") or 0,
        reverse=True,
    )[:MAX_MP4_QUALITIES]


def download_url(video_url, download_type, format_id=None):
    params = {
        "url": video_url,
        "type": download_type,
    }

    if format_id:
        params["format_id"] = format_id

    return f"{request.host_url.rstrip('/')}/download?{urlencode(params)}"


def build_servers(info, video_url):
    servers = []

    for item in mp4_formats(info):
        height = item.get("height")
        format_id = item.get("format_id")

        if not height or not format_id:
            continue

        servers.append(
            {
                "name": f"MP4 {height}p",
                "url": download_url(video_url, "mp4", format_id),
                "type": "mp4",
            }
        )

    servers.append(
        {
            "name": "MP3 Audio",
            "url": download_url(video_url, "mp3"),
            "type": "mp3",
        }
    )

    return servers


def safe_filename(value, fallback):
    name = re.sub(r'[<>:"/\\|?*\x00-\x1F]', "", str(value or ""))
    name = re.sub(r"\s+", " ", name).strip()
    return (name or fallback)[:120]


def validate_type(download_type):
    value = str(download_type or "mp3").lower().strip()
    if value not in ("mp3", "mp4"):
        raise ApiError("type must be mp3 or mp4", 400)
    return value


def validate_format_id(format_id):
    value = str(format_id or "").strip()
    if not value:
        return None

    if not re.match(r"^[a-zA-Z0-9_.-]+$", value):
        raise ApiError("format_id is invalid", 400)

    return value


def find_output_file(temp_dir, extension):
    files = [
        os.path.join(temp_dir, item)
        for item in os.listdir(temp_dir)
        if os.path.isfile(os.path.join(temp_dir, item))
    ]

    preferred = [
        item for item in files if item.lower().endswith(f".{extension}")
    ]

    if preferred:
        return preferred[0]

    if files:
        return files[0]

    raise ApiError("download file was not created", 500)


def send_temp_file(file_path, temp_dir, filename, mimetype):
    response = send_file(
        file_path,
        as_attachment=True,
        download_name=filename,
        mimetype=mimetype,
    )
    response.call_on_close(lambda: shutil.rmtree(temp_dir, ignore_errors=True))
    return response


def download_mp3(video_url, title):
    def worker(proxy, cookie_source):
        temp_dir = tempfile.mkdtemp(prefix="webfluxbox-")
        output_template = os.path.join(temp_dir, "download.%(ext)s")
        options = build_ytdlp_options(
            {
                **ytdlp_base_options(),
                "format": "bestaudio/best",
                "outtmpl": output_template,
                "postprocessors": [
                    {
                        "key": "FFmpegExtractAudio",
                        "preferredcodec": "mp3",
                        "preferredquality": "192",
                    }
                ],
            },
            proxy,
            cookie_source,
        )

        try:
            with YoutubeDL(options) as ydl:
                ydl.extract_info(video_url, download=True)

            file_path = find_output_file(temp_dir, "mp3")
            filename = f"{safe_filename(title, 'webfluxbox-audio')}.mp3"
            return send_temp_file(file_path, temp_dir, filename, "audio/mpeg")
        except Exception:
            shutil.rmtree(temp_dir, ignore_errors=True)
            raise

    return run_ytdlp_with_retries("mp3 download", worker)


def download_mp4(video_url, title, format_id):
    selected_format = (
        f"{format_id}+bestaudio[ext=m4a]/{format_id}+bestaudio/{format_id}"
        if format_id
        else "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
    )

    def worker(proxy, cookie_source):
        temp_dir = tempfile.mkdtemp(prefix="webfluxbox-")
        output_template = os.path.join(temp_dir, "download.%(ext)s")
        options = build_ytdlp_options(
            {
                **ytdlp_base_options(),
                "format": selected_format,
                "outtmpl": output_template,
                "merge_output_format": "mp4",
            },
            proxy,
            cookie_source,
        )

        try:
            with YoutubeDL(options) as ydl:
                ydl.extract_info(video_url, download=True)

            file_path = find_output_file(temp_dir, "mp4")
            filename = f"{safe_filename(title, 'webfluxbox-video')}.mp4"
            return send_temp_file(file_path, temp_dir, filename, "video/mp4")
        except Exception:
            shutil.rmtree(temp_dir, ignore_errors=True)
            raise

    return run_ytdlp_with_retries("mp4 download", worker)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/", methods=["GET"])
def home():
    return send_from_directory(app.root_path, "index.html")


@app.route("/VDown/", methods=["GET"])
@app.route("/VDown/index.html", methods=["GET"])
def vdown_page():
    return send_from_directory(os.path.join(app.root_path, "VDown"), "index.html")


@app.route("/<path:filename>", methods=["GET"])
def public_file(filename):
    normalized = filename.replace("\\", "/").strip("/")

    if "/" in normalized or normalized not in PUBLIC_FILES:
        raise NotFound()

    return send_from_directory(app.root_path, normalized)


@app.route("/info", methods=["POST", "OPTIONS"])
def info():
    if request.method == "OPTIONS":
        return ("", 204)

    payload = request.get_json(silent=True) or {}
    video_url = validate_url(payload.get("url"))

    metadata = extract_metadata(video_url)

    return jsonify(
        {
            "platform": detect_platform(video_url),
            "title": metadata.get("title") or "Untitled video",
            "thumbnail": best_thumbnail(metadata),
            "servers": build_servers(metadata, video_url),
        }
    )


@app.route("/download", methods=["GET"])
def download():
    video_url = validate_url(request.args.get("url"))
    download_type = validate_type(request.args.get("type"))
    format_id = validate_format_id(request.args.get("format_id"))

    metadata = extract_metadata(video_url)
    title = metadata.get("title") or "webfluxbox-download"

    if download_type == "mp4":
        return download_mp4(video_url, title, format_id)

    return download_mp3(video_url, title)


@app.errorhandler(ApiError)
def handle_api_error(error):
    return jsonify({"error": error.message}), error.status_code


@app.errorhandler(HTTPException)
def handle_http_error(error):
    if error.code == 404 and not request.path.startswith(("/api", "/info", "/download", "/health")):
        if os.path.exists(os.path.join(app.root_path, "404.html")):
            return send_from_directory(app.root_path, "404.html"), 404

    return jsonify({"error": error.description or error.name}), error.code


@app.errorhandler(Exception)
def handle_unexpected_error(error):
    app.logger.exception(error)
    return jsonify({"error": "internal server error"}), 500


if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=int(os.getenv("PORT", 5000)),
        debug=os.getenv("FLASK_DEBUG", "0") == "1",
        use_reloader=False,
    )
