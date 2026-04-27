from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import yt_dlp, os, tempfile, re, shutil

app = Flask(__name__)
CORS(app, origins=["https://webflux.me", "https://www.webflux.me"])

MAX_DURATION = 3600
FORMATS = {
    "mp3":  {"format":"bestaudio/best","postprocessors":[{"key":"FFmpegExtractAudio","preferredcodec":"mp3","preferredquality":"0"}]},
    "m4a":  {"format":"bestaudio/best","postprocessors":[{"key":"FFmpegExtractAudio","preferredcodec":"m4a","preferredquality":"5"}]},
    "360p": {"format":"bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]/best","merge_output_format":"mp4"},
    "720p": {"format":"bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best","merge_output_format":"mp4"},
    "1080p":{"format":"bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]/best","merge_output_format":"mp4"},
    "best": {"format":"bestvideo+bestaudio/best","merge_output_format":"mp4"},
}

def valid_url(u):
    return bool(re.match(r'^https?://',u)) and len(u)<2048

def get_cookies_path():
    import shutil, tempfile
    env_path = os.environ.get("YTDLP_COOKIES_FILE")
    source = None
    if env_path and os.path.exists(env_path):
        source = env_path
    else:
        render_secret_path = "/etc/secrets/cookies.txt"
        if os.path.exists(render_secret_path):
            source = render_secret_path
        else:
            default_path = os.path.join(os.path.dirname(__file__), "cookies.txt")
            if os.path.exists(default_path):
                source = default_path
    if source is None:
        return None
    tmp = tempfile.NamedTemporaryFile(
        delete=False, suffix=".txt", mode="w", encoding="utf-8"
    )
    with open(source, "r", encoding="utf-8") as f:
        tmp.write(f.read())
    tmp.close()
    return tmp.name

@app.route("/")
@app.route("/health")
def health():
    return jsonify({"status":"ok","service":"MayBox API"})

@app.route("/info", methods=["GET", "POST"])
def get_info():
    data = request.get_json(silent=True) or {}
    url = ((request.args.get("url") if request.method == "GET" else data.get("url")) or "").strip()
    if not url or not valid_url(url):
        return jsonify({"error":"Invalid URL"}),400
    print("DEBUG cookies path:", get_cookies_path())
    opts = {
        "quiet":False,"no_warnings":False,"skip_download":True,
        "no_check_certificates":True,
        "extractor_args":{"dailymotion":{"cdn":["none"]}},
        "http_headers":{"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
    }
    cookies_path = get_cookies_path()
    if cookies_path and os.path.exists(cookies_path):
        _tmp = tempfile.mkstemp(suffix=".txt")[1]
        shutil.copy2(cookies_path, _tmp)
        cookies_path = _tmp
    if cookies_path:
        opts["cookiefile"] = cookies_path
        opts["no_cookies_write"] = True
    print("DEBUG opts cookiefile:", opts.get("cookiefile"))
    try:
        ydl = yt_dlp.YoutubeDL(opts)
        try:
            ydl.cookiejar.filename = None
        except Exception:
            pass
        try:
            info = ydl.extract_info(url, download=False)
        finally:
            pass
        dur = info.get("duration",0)
        if dur and dur > MAX_DURATION:
            return jsonify({"error":"Video too long (max 1 hour)"}),400
        thumbs = info.get("thumbnails",[])
        thumb = next((t["url"] for t in reversed(thumbs) if t.get("url")), info.get("thumbnail"))
        s = int(dur or 0); h,r=divmod(s,3600); m,sec=divmod(r,60)
        dur_str = f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"
        return jsonify({"title":info.get("title","Video"),"uploader":info.get("uploader",""),"duration_str":dur_str,"thumbnail":thumb,"extractor":info.get("extractor_key","")})
    except Exception as e:
        return jsonify({"error": str(e)}),400

@app.route("/download", methods=["POST"])
def download_video():
    data = request.get_json(silent=True) or {}
    url = (data.get("url") or "").strip()
    fmt = (data.get("format") or "720p").strip().lower()
    if not url or not valid_url(url): return jsonify({"error":"Invalid URL"}),400
    if fmt not in FORMATS: return jsonify({"error":"Invalid format"}),400
    with tempfile.TemporaryDirectory() as tmp:
        opts = {
            "outtmpl":os.path.join(tmp,"%(title).60s.%(ext)s"),
            "quiet":True,"no_warnings":True,"no_check_certificates":True,
            "no_playlist":True,"no_mtime":True,"retries":3,
            "extractor_args":{"dailymotion":{"cdn":["none"]}},
            "http_headers":{"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
        }
        cookies_path = get_cookies_path()
        if cookies_path and os.path.exists(cookies_path):
            _tmp = tempfile.mkstemp(suffix=".txt")[1]
            shutil.copy2(cookies_path, _tmp)
            cookies_path = _tmp
        if cookies_path:
            opts["cookiefile"] = cookies_path
            opts["no_cookies_write"] = True
        opts.update(FORMATS[fmt])
        try:
            ydl = yt_dlp.YoutubeDL(opts)
            try:
                ydl.cookiejar.filename = None
            except Exception:
                pass
            try:
                ydl.download([url])
            finally:
                pass
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
