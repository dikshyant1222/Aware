// A single ordered queue shared by captions and avatar playback. No timer can
// start an old caption after Stop/Clear, and a busy avatar never replaces a line.
export class CaptionPlayback {
  constructor({play, stop, onStart = () => {}, onComplete = () => {}, onError = () => {}}) {
    Object.assign(this, {play, stop, onStart, onComplete, onError});
    this.pending = [];
    this.seen = new Set();
    this.current = null;
    this.paused = false;
    this.stats = {accepted: 0, started: 0, completed: 0, cancelled: 0, failed: 0};
  }

  enqueue(entries) {
    for (const entry of entries) {
      const {id, text} = entry;
      const key = String(id);
      if (!String(text).trim() || this.seen.has(key)) continue;
      this.seen.add(key);
      this.stats.accepted++;
      this.pending.push({...entry, id: key, text: String(text)});
    }
    this.drain();
  }

  drain() {
    while (!this.paused && !this.current && this.pending.length) {
      const entry = this.pending.shift();
      this.current = entry;
      try {
        this.play(entry);
        this.stats.started++;
        this.onStart(entry);
      } catch (error) {
        this.current = null;
        this.stats.failed++;
        this.stop();
        this.onError(error, entry);
      }
    }
  }

  finish(id) {
    if (this.current?.id !== String(id)) return;
    const entry = this.current;
    this.current = null;
    this.stats.completed++;
    this.onComplete(entry);
    this.drain();
  }

  setPaused(paused) {
    this.paused = paused;
    this.drain();
  }

  reset() {
    const cancelled = this.current;
    this.pending = [];
    this.seen.clear();
    this.current = null;
    this.stop();
    if (cancelled) {this.stats.cancelled++; this.onComplete(cancelled);}
  }
}
