import {test} from 'node:test';
import assert from 'node:assert/strict';
import {CaptionPlayback} from '../app/src/main/assets/avatar/caption-playback.js';
import {compileMessage} from '../app/src/main/assets/avatar/common-signs.js';

function player(overrides = {}) {
  const events = [];
  const queue = new CaptionPlayback({
    play: e => events.push(['play', e.id, e.text]),
    stop: () => events.push(['stop']),
    onStart: e => events.push(['start', e.id]),
    onComplete: e => events.push(['complete', e.id]),
    onError: (_, e) => events.push(['error', e.id]),
    ...overrides,
  });
  return {queue, events};
}
const line = (id, text = 'Hello') => ({id: String(id), text});

test('rapid finalized captions play once, in order, without replacement', () => {
  const {queue, events} = player();
  queue.enqueue([line(1), line(2)]);
  queue.enqueue([line(2), line(3), line(4)]);
  assert.equal(queue.current.id, '1');
  for (const id of [1, 2, 3, 4]) queue.finish(id);
  assert.deepEqual(events.filter(e => e[0] === 'play').map(e => e[1]), ['1', '2', '3', '4']);
  assert.equal(queue.current, null);
});

test('Stop/Clear cancels current and queued signs; late completion cannot restart them', () => {
  const {queue, events} = player();
  queue.enqueue([line(1), line(2)]);
  queue.reset();
  queue.finish(1);
  assert.equal(queue.current, null);
  assert.equal(queue.pending.length, 0);
  queue.enqueue([line(3)]);
  queue.finish(1);
  assert.equal(queue.current.id, '3');
  assert.deepEqual(events.filter(e => e[0] === 'play').map(e => e[1]), ['1', '3']);
});

test('background pause preserves the queue until the screen resumes', () => {
  const {queue, events} = player();
  queue.setPaused(true);
  queue.enqueue([line(1), line(2)]);
  assert.equal(events.length, 0);
  queue.setPaused(false);
  queue.setPaused(true);
  queue.finish(1);
  assert.equal(queue.current, null);
  queue.setPaused(false);
  assert.equal(queue.current.id, '2');
});

test('a clip error is reported and does not strand the next caption', () => {
  const {queue, events} = player({play: e => {if (e.id === '1') throw Error('bad clip');}});
  queue.enqueue([line(1), line(2)]);
  assert.deepEqual(events.filter(e => e[0] === 'error'), [['error', '1']]);
  assert.equal(queue.current.id, '2');
});

test('actual caption compiler produces finite clips for signs, spelling, and quoted speech', () => {
  for (const text of ['Hello, thank you', 'Please help me', "I'm Zoe.\\\nShe said: \"hello\"", 'Yes no stop']) {
    const {clip, marks} = compileMessage(text);
    assert.ok(marks.length > 0, text);
    assert.ok(Number.isFinite(clip.duration) && clip.duration > 0, text);
    assert.ok(clip.tracks.length > 0, text);
    assert.ok(marks.every(m => m.start >= 0 && m.end <= clip.duration + .001), text);
  }
});

test('queue preserves source word offsets for the native highlight callback', () => {
  let started;
  const {queue} = player({onStart: entry => {started = entry;}});
  queue.enqueue([{id:'7:1',lineId:'7',text:'thank you',start:6,end:15}]);
  assert.deepEqual(started, {id:'7:1',lineId:'7',text:'thank you',start:6,end:15});
});

test('live clips retain real motion while avoiding studio-length fingerspelling delays', () => {
  for (const text of ['Hello', 'thank you', 'world', '21']) {
    const slow=compileMessage(text);
    const live=compileMessage(text,false,{live:true});
    assert.ok(live.clip.duration > 0 && live.clip.duration < slow.clip.duration, text);
    assert.ok(live.clip.tracks.some(track => track.values.some((value,index) => index>=4 && Math.abs(value-track.values[index%4])>.001)), text);
    assert.ok(live.marks.every(mark=>mark.start>=0 && mark.end<=live.clip.duration+.001),text);
  }
});
