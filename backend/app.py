from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from werkzeug.exceptions import HTTPException
import yt_dlp, os, tempfile, re, shutil
from urllib.parse import urlencode

app = Flask(__name__)
ALLOWED_ORIGINS = {"https://webflux.me", "https://www.webflux.me"}
CORS(app, resources={r"/*":{"origins":list(ALLOWED_ORIGINS)}})

@app.after_request
def add_cors_headers(response):
    origin = request.headers.get("Origin")
    if origin in ALLOWED_ORIGINS:
        response.headers["Access-Control-Allow-Origin"] = origin
        response.headers["Vary"] = "Origin"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    return response

@app.errorhandler(Exception)
def handle_unexpected_error(error):
    code = error.code if isinstance(error, HTTPException) else 500
    return jsonify({"error":str(error)}), code

MAX_DURATION = 3600
VIDEO_HEIGHTS = (144, 240, 360, 480, 720, 1080)
YOUTUBE_CLIENT_SETS = (
    ["tv_downgraded", "web", "web_safari"],
    ["web", "web_safari", "mweb"],
    ["ios", "android"],
    ["android_vr", "ios", "android"],
    ["mweb"],
)

def js_runtime_options():
    deno_path = os.environ.get("DENO_PATH")
    return {"deno":{"path":deno_path}} if deno_path else {"deno":{}}

def extractor_args(youtube_clients=None):
    args = {"dailymotion":{"cdn":["none"]}}
    if youtube_clients:
        args["youtube"] = {"player_client":youtube_clients}
    return args

def mp4_format(max_height=None):
    height = f"[height<={max_height}]" if max_height else ""
    return (
        f"bestvideo{height}[ext=mp4]+bestaudio[ext=m4a]/"
        f"bestvideo{height}[ext=mp4]+bestaudio/"
        f"best{height}[ext=mp4]/"
        f"best{height}/"
        "bestvideo[ext=mp4]+bestaudio[ext=m4a]/"
        "bestvideo[ext=mp4]+bestaudio/"
        "best[ext=mp4]/best"
    )

FORMATS = {
    "mp3":  {"format":"bestaudio/best","postprocessors":[{"key":"FFmpegExtractAudio","preferredcodec":"mp3","preferredquality":"0"}]},
    "m4a":  {"format":"bestaudio/best","postprocessors":[{"key":"FFmpegExtractAudio","preferredcodec":"m4a","preferredquality":"5"}]},
    "144p": {"format":mp4_format(144),"merge_output_format":"mp4"},
    "240p": {"format":mp4_format(240),"merge_output_format":"mp4"},
    "360p": {"format":mp4_format(360),"merge_output_format":"mp4"},
    "480p": {"format":mp4_format(480),"merge_output_format":"mp4"},
    "720p": {"format":mp4_format(720),"merge_output_format":"mp4"},
    "1080p":{"format":mp4_format(1080),"merge_output_format":"mp4"},
    "best": {"format":mp4_format(),"merge_output_format":"mp4"},
    "mp4":  {"format":mp4_format(),"merge_output_format":"mp4"},
}

INFO_FORMATS = [
    ("MP3 Audio", "mp3", "mp3"),
    ("MP4 360p", "360p", "mp4"),
    ("MP4 720p", "720p", "mp4"),
    ("MP4 1080p", "1080p", "mp4"),
    ("Best Quality", "best", "mp4"),
]

def valid_url(u):
    return bool(re.match(r'^https?://',u)) and len(u)<2048

def detect_platform(u):
    host = re.sub(r"^www\.", "", re.sub(r"^m\.", "", re.sub(r"^https?://", "", u.lower())).split("/")[0])
    if "youtube.com" in host or "youtu.be" in host:
        return "youtube"
    if "tiktok.com" in host:
        return "tiktok"
    if "instagram.com" in host:
        return "instagram"
    if "twitter.com" in host or "x.com" in host:
        return "x/twitter"
    if "facebook.com" in host or "fb.watch" in host:
        return "facebook"
    return "unknown"

def get_format_options(fmt):
    if fmt in FORMATS:
        return dict(FORMATS[fmt])
    match = re.fullmatch(r"(\d{3,4})p", fmt)
    if match:
        return {"format":mp4_format(int(match.group(1))),"merge_output_format":"mp4"}
    return None

def available_mp4_heights(info):
    heights = set()
    for fmt in info.get("formats") or []:
        height = fmt.get("height")
        if fmt.get("ext") == "mp4" and height and fmt.get("vcodec") != "none":
            heights.add(int(height))
    return sorted(heights, reverse=True)

def build_servers(video_url, info=None):
    base = request.host_url.rstrip("/") + "/download"
    servers = [{"name":"MP3 Audio","url":base + "?" + urlencode({"url":video_url,"format":"mp3"}),"type":"mp3"}]
    heights = available_mp4_heights(info or {})
    if not heights:
        servers.append({
            "name":"Best MP4",
            "url":base + "?" + urlencode({"url":video_url,"format":"mp4"}),
            "type":"mp4"
        })
        return servers
    for height in heights[:4]:
        servers.append({
            "name":f"MP4 {height}p",
            "url":base + "?" + urlencode({"url":video_url,"format":f"{height}p"}),
            "type":"mp4"
        })
    return servers[:5]

def get_cookies_path():
    env_path = os.environ.get("YTDLP_COOKIES_FILE")
    source = None
    if env_path and os.path.exists(env_path):
        source = env_path
    else:
        render_secret_path = "/etc/secrets/cookies.txt"
        if os.path.exists(render_secret_path):
            source = render_secret_path
    if source is None:
        return None
    fd, tmp_path = tempfile.mkstemp(suffix=".txt")
    os.close(fd)
    shutil.copyfile(source, tmp_path)
    return tmp_path

@app.route("/")
@app.route("/health")
def health():
    return jsonify({"status":"ok","service":"webflux API"})

@app.route("/info", methods=["GET", "POST"])
def get_info():
    data = request.get_json(silent=True) or {}
    url = ((request.args.get("url") if request.method == "GET" else data.get("url")) or "").strip()
    if not url or not valid_url(url):
        return jsonify({"error":"Invalid URL"}),400
    opts = {
        "quiet":False,"no_warnings":False,"skip_download":True,
        "ignore_no_formats_error":True,
        "socket_timeout":20,
        "js_runtimes":js_runtime_options(),
        "no_check_certificates":True,
        "extractor_args":extractor_args(),
        "http_headers":{"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
    }
    try:
        cookies_path = get_cookies_path()
        if cookies_path:
            opts["cookiefile"] = cookies_path
            opts["no_cookies_write"] = True
        def extract_with_options(info_opts):
            ydl = yt_dlp.YoutubeDL(info_opts)
            try:
                ydl.cookiejar.filename = None
            except Exception:
                pass
            return ydl.extract_info(url, download=False)

        info = extract_with_options(opts)
        dur = info.get("duration",0)
        if dur and dur > MAX_DURATION:
            return jsonify({"error":"Video too long (max 1 hour)"}),400
        thumbs = info.get("thumbnails",[])
        thumb = next((t["url"] for t in reversed(thumbs) if t.get("url")), info.get("thumbnail"))
        s = int(dur or 0); h,r=divmod(s,3600); m,sec=divmod(r,60)
        dur_str = f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"
        platform = detect_platform(url)
        return jsonify({"platform":platform,"title":info.get("title","Video"),"uploader":info.get("uploader",""),"duration_str":dur_str,"thumbnail":thumb,"extractor":info.get("extractor_key",""),"servers":build_servers(url, info)})
    except Exception as e:
        return jsonify({"error": str(e)}),400

@app.route("/download", methods=["GET", "POST"])
def download_video():
    data = request.get_json(silent=True) or {}
    url = ((request.args.get("url") if request.method == "GET" else data.get("url")) or "").strip()
    fmt = ((request.args.get("format") or request.args.get("type")) if request.method == "GET" else (data.get("format") or data.get("type")) or "720p").strip().lower()
    if not url or not valid_url(url): return jsonify({"error":"Invalid URL"}),400
    format_opts = get_format_options(fmt)
    if not format_opts: return jsonify({"error":"Invalid format"}),400
    with tempfile.TemporaryDirectory() as tmp:
        opts = {
            "outtmpl":os.path.join(tmp,"%(title).60s.%(ext)s"),
            "quiet":True,"no_warnings":True,"no_check_certificates":True,
            "no_playlist":True,"no_mtime":True,"retries":3,
            "js_runtimes":js_runtime_options(),
            "extractor_args":extractor_args(),
            "http_headers":{"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
        }
        try:
            cookies_path = get_cookies_path()
            if cookies_path:
                opts["cookiefile"] = cookies_path
                opts["no_cookies_write"] = True
            opts.update(format_opts)
            def run_download(download_opts):
                ydl = yt_dlp.YoutubeDL(download_opts)
                try:
                    ydl.cookiejar.filename = None
                except Exception:
                    pass
                ydl.download([url])

            try:
                run_download(opts)
            except Exception as e:
                if "Requested format is not available" in str(e):
                    opts.update(get_format_options("mp4"))
                    last_error = e
                    for clients in (None, *YOUTUBE_CLIENT_SETS):
                        retry_opts = dict(opts)
                        retry_opts["extractor_args"] = extractor_args(clients)
                        try:
                            run_download(retry_opts)
                            last_error = None
                            break
                        except Exception as retry_error:
                            last_error = retry_error
                    if last_error:
                        raise last_error
                else:
                    raise
            files = [f for f in os.listdir(tmp) if os.path.isfile(os.path.join(tmp,f))]
            if not files: return jsonify({"error":"Download failed"}),500
            fpath = os.path.join(tmp,files[0])
            ext = os.path.splitext(files[0])[1].lower()
            mime = {".mp4":"video/mp4",".mp3":"audio/mpeg",".m4a":"audio/mp4",".webm":"video/webm"}.get(ext,"application/octet-stream")
            return send_file(fpath,as_attachment=True,download_name=files[0],mimetype=mime)
        except Exception as e:
            return jsonify({"error": str(e)}),400

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT",5000)))
