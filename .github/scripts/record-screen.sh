#!/usr/bin/env bash
set -euo pipefail

VIDEO=screen.mp4
PID_FILE=ffmpeg.pid
START_FILE=ffmpeg.start

case "${1:-}" in
  start)
    nohup ffmpeg -nostdin -y \
      -f x11grab -video_size 1920x1080 -framerate 5 -i "${DISPLAY:-:99}" \
      -codec:v libx264 -preset ultrafast -crf 30 -pix_fmt yuv420p \
      "$VIDEO" > ffmpeg.log 2>&1 &
    echo $! > "$PID_FILE"
    date +%s > "$START_FILE"
    sleep 2
    if ! kill -0 "$(cat "$PID_FILE")" 2> /dev/null; then
      echo "ffmpeg did not start:"
      cat ffmpeg.log
      exit 1
    fi
    echo "Recording $DISPLAY to $VIDEO"
    ;;

  stop)
    [ -f "$PID_FILE" ] || { echo "Nothing was recording"; exit 0; }
    pid=$(cat "$PID_FILE")
    kill -INT "$pid" 2> /dev/null || true
    for _ in $(seq 1 40); do
      kill -0 "$pid" 2> /dev/null || break
      sleep 0.5
    done
    kill -9 "$pid" 2> /dev/null || true
    ls -lh "$VIDEO" 2> /dev/null || echo "No video was produced, see ffmpeg.log"
    ;;

  *)
    echo "Usage: $0 start|stop" >&2
    exit 2
    ;;
esac
