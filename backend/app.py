from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import yt_dlp, os, tempfile, re
from yt_dlp.networking.impersonate import ImpersonateTarget

app = Flask(__name__)
CORS(app)

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

IMPERSONATE = ImpersonateTarget("chrome")

@app.route("/")
def health():
    return jsonify({"status":"ok","service":"MayBox API"})

@app.route("/info", methods=["POST"])
def get_info():
    data = request.get_json(silent=True) or {}
    url = (data.get("url") or "").strip()
    if not url or not valid_url(url):
        return jsonify({"error":"Invalid URL"}),400
    opts = {"quiet":True,"no_warnings":True,"skip_download":True,"no_check_certificates":True,"impersonate":IMPERSONATE}
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        dur = info.get("duration",0)
        if dur and dur > MAX_DURATION:
            return jsonify({"error":"Video too long (max 1 hour)"}),400
        thumbs = info.get("thumbnails",[])
        thumb = next((t["url"] for t in reversed(thumbs) if t.get("url")), info.get("thumbnail"))
        s = int(dur or 0); h,r=divmod(s,3600); m,sec=divmod(r,60)
        dur_str = f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"
        return jsonify({"title":info.get("title","Video"),"uploader":info.get("uploader",""),"duration_str":dur_str,"thumbnail":thumb,"extractor":info.get("extractor_key","")})
    except Exception as e:
        return jsonify({"error":"Could not fetch video info"}),400

@app.route("/download", methods=["POST"])
def download_video():
    data = request.get_json(silent=True) or {}
    url = (data.get("url") or "").strip()
    fmt = (data.get("format") or "720p").strip().lower()
    if not url or not valid_url(url): return jsonify({"error":"Invalid URL"}),400
    if fmt not in FORMATS: return jsonify({"error":"Invalid format"}),400
    with tempfile.TemporaryDirectory() as tmp:
        opts = {"outtmpl":os.path.join(tmp,"%(title).60s.%(ext)s"),"quiet":True,"no_warnings":True,"no_check_certificates":True,"no_playlist":True,"no_mtime":True,"retries":3,"impersonate":IMPERSONATE}
        opts.update(FORMATS[fmt])
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                ydl.download([url])
            files = [f for f in os.listdir(tmp) if os.path.isfile(os.path.join(tmp,f))]
            if not files: return jsonify({"error":"Download failed"}),500
            fpath = os.path.join(tmp,files[0])
            ext = os.path.splitext(files[0])[1].lower()
            mime = {".mp4":"video/mp4",".mp3":"audio/mpeg",".m4a":"audio/mp4",".webm":"video/webm"}.get(ext,"application/octet-stream")
            return send_file(fpath,as_attachment=True,download_name=files[0],mimetype=mime)
        except Exception as e:
            return jsonify({"error":"Download failed"}),400

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT",5000)))
