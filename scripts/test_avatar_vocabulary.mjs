import {test} from 'node:test';
import assert from 'node:assert/strict';
import {compileMessage, VOCABULARY} from '../app/src/main/assets/avatar/common-signs.js';
import {SIGNS, SIGNABLE_WORDS, lookupSign} from '../app/src/main/assets/avatar/vendor/sign-dictionary.js';
import {HANDSHAPES} from '../app/src/main/assets/avatar/vendor/sign-handshapes.js';
import {EVERYDAY_SIGNS} from '../app/src/main/assets/avatar/everyday-signs.js';

test('every dictionary entry is available to live captions with finite moving clips', () => {
  assert.equal(VOCABULARY.length, 64);
  assert.deepEqual(new Set(VOCABULARY.map(([, key]) => key)), new Set(Object.keys(SIGNS)));
  for (const word of Object.keys(SIGNS)) {
    const result = compileMessage(word, false, {live: true, continuous: true});
    assert.equal(result.marks.length, 1, word);
    assert.equal(result.marks[0].kind, 'sign', word);
    assert.deepEqual(result.spelled, [], word);
    assert.ok(result.clip.duration > .1 && result.clip.duration < 4, word);
    assert.ok(result.clip.tracks.every(track => track.values.every(Number.isFinite)), word);
    assert.ok(result.clip.tracks.some(track => track.values.some((v, i) => i >= 4 && Math.abs(v - track.values[i % 4]) > .001)), word);
    assert.ok(result.cues.every(c => c.start >= 0 && c.end <= result.clip.duration + .001), word);
    assert.ok(Math.abs(result.cues.at(-1).end - result.clip.duration) < .001, word);
  }
});

test('every lexical variant resolves to a whole-word sign and word lists are unique', () => {
  assert.equal(SIGNABLE_WORDS.length, new Set(SIGNABLE_WORDS).size);
  for (const word of SIGNABLE_WORDS) {
    assert.ok(lookupSign(word), word);
    assert.equal(compileMessage(word, false, {live: true}).marks[0].kind, 'sign', word);
  }
  assert.deepEqual(compileMessage('Mom dad eating drinking waiting books').spelled, []);
});

test('all authored handshapes exist on both hands and new motions retain their references', () => {
  for (const [word, entry] of Object.entries(SIGNS)) {
    for (const phase of entry.phases) for (const side of ['right', 'left', 'both']) {
      const shape = phase[side]?.shape;
      if (shape) assert.ok(HANDSHAPES[shape], `${word}: ${side} ${shape}`);
    }
    if (EVERYDAY_SIGNS[word]) assert.match(entry.source, /^https:\/\/www\.lifeprint\.com\/asl101\/pages-signs\//);
  }
});

test('possession, liking, needing, eating and food have distinct motions', () => {
  for (const [a, b] of [['MY', 'ME'], ['MINE', 'MY'], ['YOUR', 'YOU'], ['NEED', 'WANT'], ['LIKE', 'GOOD'], ['EAT', 'FOOD'], ['GO', 'COME'], ['SIT', 'STAND']]) {
    assert.equal(lookupSign(a).name, a);
    assert.notDeepEqual(SIGNS[a].phases, SIGNS[b].phases);
    assert.notDeepEqual(compileMessage(a).clip.tracks, compileMessage(b).clip.tracks);
  }
  for (const word of ['WATCH', 'LOOK', 'EVERYONE', 'GREAT', 'OK']) assert.equal(lookupSign(word), null, word);
});

test('new words, thank-you grouping, unknown names, and spelling-only mode coexist', () => {
  const result = compileMessage('My mother needs water. Thank you Zoe!', false, {live: true, continuous: true});
  assert.deepEqual(result.signed, ['MY', 'MOTHER', 'NEEDS', 'WATER', 'THANK YOU']);
  assert.deepEqual(result.spelled, ['ZOE']);
  assert.deepEqual(result.marks.filter(m => m.kind === 'letter').map(m => m.letter), ['Z', 'O', 'E']);
  assert.deepEqual(compileMessage('Water', true).signed, []);
});

test('two-hand signs retain the non-dominant hand and within-sign finger changes', () => {
  for (const word of ['FAMILY', 'WHEN', 'HOW', 'SCHOOL', 'BOOK', 'READ', 'WRITE', 'WAIT', 'SIT', 'STAND', 'TIRED', 'HURT', 'SICK']) {
    const result = compileMessage(word, false, {live: true, continuous: true});
    assert.equal(result.marks[0].twoHanded, true, word);
    assert.ok(result.cues.every(c => c.left !== 'RELAXED'), word);
  }
  assert.deepEqual(compileMessage('WHY').cues.slice(0, 2).map(c => c.right), ['5', 'Y']);
  assert.deepEqual(compileMessage('SLEEP').cues.slice(0, 2).map(c => c.right), ['5', 'FLAT_O']);
  assert.deepEqual(compileMessage('WAIT').cues.slice(0, 4).map(c => c.right), ['WAIT_A', 'WAIT_B', 'WAIT_A', 'WAIT_B']);
});
