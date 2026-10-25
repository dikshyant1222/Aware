// Authored citation-form motions. References and scope: VOCABULARY_SOURCES.md.
// Keep every phase explicit: the IK solver starts each phase from its base pose.
const source = path => `https://www.lifeprint.com/asl101/pages-signs/${path}.htm`;
const at = (anchor, out, up, forward) => ({anchor, out, up, forward});
const hand = (shape, location, fingers = 'up', palm = 'back') => ({shape, at: location, fingers, palm});
const contact = (shape, to, part, offsets = {}, fingers = 'up', palm = 'back') =>
  ({shape, touch: {to, part, gap: .008, ...offsets}, fingers, palm});
const phase = (right, t = .25, hold = 0, extra = {}) => ({right, t, hold, ...extra});
const sign = (path, gloss, phases) => ({source: source(path), gloss, phases});
const chest = (out = .13, up = -.02, forward = .25) => at('sternum', out, up, forward);
const basePalm = hand('FLAT', chest(.06, -.1, .25), ['forward', 'in'], 'up');
const tapMouth = (shape, forward = 0) => contact(shape, 'mouth', 'fingertips', {out: .02, forward}, ['up', 'in']);
const mouthAway = shape => contact(shape, 'mouth', 'fingertips', {out: .02, forward: .055}, ['up', 'in']);
const possessive = contact('FLAT', 'sternum', 'palm', {forward: .025}, ['up', 'in']);
const awayPossessive = contact('FLAT', 'sternum', 'palm', {forward: .07}, ['up', 'in']);
const parent = anchor => [
  phase(contact('5', anchor, 'thumbtip', {out: .025, forward: .045}, 'up'), .3),
  phase(contact('5', anchor, 'thumbtip', {out: .025, forward: .008}, 'up'), .18, .16),
];
const question = {face: 'wh', head: {nod: 3}};
const sideShake = (shape, location, extra = {}) => [-1, 1, -1, 1].map((s, i) =>
  phase(hand(shape, location, ['up', s < 0 ? 'in' : 'out'], 'forward'), i ? .16 : .28, i === 3 ? .12 : 0, extra));
const schoolUp = {shape: 'FLAT', touch: {part: 'palm', on: 'palm', up: .075}, fingers: ['forward', 'in'], palm: 'down'};
const schoolDown = {shape: 'FLAT', touch: {part: 'palm', on: 'palm', gap: .008}, fingers: ['forward', 'in'], palm: 'down'};
const book = palm => ({shape: 'FLAT', at: chest(palm === 'in' ? .025 : .095, -.09, .28), fingers: 'forward', palm});
const sitBase = hand('U', chest(.04, -.1, .29), ['forward', 'in'], 'down');
const sitHand = up => ({shape: 'BENT_V', touch: {part: 'fingers', on: 'back', up}, fingers: ['forward', 'in'], palm: 'down'});
const hurt = out => ({both: hand('1', chest(out, -.04, .3), 'in', 'back'), face: 'concern'});

export const EVERYDAY_SIGNS = Object.freeze({
  WATER: sign('w/water', 'W-hand taps the mouth twice.', [
    phase(mouthAway('W'), .28), phase(tapMouth('W'), .17, .06),
    phase(mouthAway('W'), .17), phase(tapMouth('W'), .17, .14),
  ]),
  EAT: sign('e/eat', 'Pinched fingertips move to the mouth once.', [
    phase(mouthAway('FLAT_O'), .28), phase(tapMouth('FLAT_O'), .23, .16),
  ]),
  FOOD: sign('e/eat', 'Pinched fingertips tap the mouth twice.', [
    phase(mouthAway('FLAT_O'), .28), phase(tapMouth('FLAT_O'), .17, .05),
    phase(mouthAway('FLAT_O'), .17), phase(tapMouth('FLAT_O'), .17, .14),
  ]),
  DRINK: sign('d/drink', 'C-hand lifts and tips toward the mouth like a cup.', [
    phase(hand('C', at('chin', .09, -.15, .23), 'up', 'in'), .3),
    phase(hand('C', at('mouth', .07, -.04, .16), ['up', 'back'], ['in', 'up']), .34, .16),
  ]),
  BATHROOM: sign('b/bathroom', 'T-hand shakes at the wrist.', sideShake('T', chest(.18, .01, .26))),
  NEED: sign('n/need', 'X-hand bends down once at the wrist.', [
    phase(hand('X', chest(.17), 'up', 'forward'), .28),
    phase(hand('X', chest(.17, -.035), ['forward', 'down'], 'down'), .24, .16),
  ]),
  LIKE: sign('l/like', 'Middle finger and thumb draw away from the chest and close.', [
    phase(hand('OPEN_8', chest(.025, .015, .13)), .3),
    phase(hand('8', chest(.04, .015, .32)), .32, .16),
  ]),
  MY: sign('m/my', 'Flat palm contacts the chest once.', [phase(awayPossessive, .26), phase(possessive, .22, .17)]),
  MINE: sign('m/my', 'Flat palm contacts the chest twice.', [
    phase(awayPossessive, .26), phase(possessive, .17, .05), phase(awayPossessive, .17), phase(possessive, .17, .14),
  ]),
  YOUR: sign('y/your', 'Flat palm faces and moves toward the addressee.', [
    phase(hand('FLAT', chest(.02, .015, .24), 'up', 'forward'), .28),
    phase(hand('FLAT', chest(.02, .015, .4), 'up', 'forward'), .26, .17),
  ]),
  MOTHER: sign('m/mom', 'Thumb of a spread hand touches the chin.', parent('chin')),
  FATHER: sign('d/dad', 'Thumb of a spread hand touches the forehead.', parent('forehead')),
  FAMILY: sign('f/family', 'Two F-hands trace an enclosing circle.', [
    {t: .3, both: hand('F', chest(.035, .015, .24), 'up', 'in')},
    {t: .23, both: hand('F', chest(.18, .015, .29), 'up', 'forward')},
    {t: .23, both: hand('F', chest(.17, .015, .4), 'up', 'out')},
    {t: .24, hold: .16, both: hand('F', chest(.035, .015, .41), 'up', 'back')},
  ]),
  WHERE: sign('w/where', 'Upright index hand pivots side to side.', sideShake('1', chest(.16, .04), question)),
  WHO: sign('w/who', 'Index finger bends twice near the chin.', ['1', 'X', '1', 'X'].map((shape, i) =>
    phase(hand(shape, at('chin', .045, -.06, .2)), i ? .17 : .28, i === 3 ? .14 : 0, question))),
  WHEN: sign('w/when', 'Dominant index circles the upright base index, then touches it.', [
    ...[[0, 0], [.055, .055], [0, .1], [-.055, .055], [0, 0]].map(([out, up], i) => ({
      ...question, t: i ? .19 : .3, hold: i === 4 ? .14 : 0,
      left: hand('1', chest(.06, -.04, .28), 'up', 'in'),
      right: {shape: '1', touch: {part: 'indextip', on: 'indextip', out, up, gap: .012}, fingers: ['forward', 'in'], palm: 'down'},
    })),
  ]),
  WHY: sign('w/why', 'Open hand leaves the forehead and closes into Y.', [
    phase(contact('5', 'forehead', 'fingertips', {out: .04, forward: .025}), .3, 0, question),
    phase(hand('Y', at('chin', .14, -.08, .3)), .32, .15, question),
  ]),
  HOW: sign('h/how', 'Curved hands roll forward from knuckles together to palms up.', [
    {...question, t: .3, both: hand('C', chest(.065, -.025, .25), 'in', ['down', 'back'])},
    {...question, t: .3, hold: .16, both: hand('C', chest(.09, -.05, .28), 'in', 'up')},
  ]),
  HOME: sign('h/home', 'Pinched fingertips touch the cheek near mouth, then near ear.', [
    phase(contact('FLAT_O', 'mouth', 'fingertips', {out: .075, forward: .015}, ['up', 'in']), .3, .08),
    phase(contact('FLAT_O', 'nose', 'fingertips', {out: .105, forward: -.015, up: -.005}, ['up', 'in']), .28, .16),
  ]),
  SCHOOL: sign('s/school', 'Dominant flat palm claps down onto the base palm twice.', [
    phase(schoolUp, .3, 0, {left: basePalm}), phase(schoolDown, .17, .04, {left: basePalm}),
    phase(schoolUp, .17, 0, {left: basePalm}), phase(schoolDown, .17, .14, {left: basePalm}),
  ]),
  BOOK: sign('b/book', 'Flat hands open like book covers twice.', [
    {t: .3, both: book('in')}, {t: .21, both: book('up'), hold: .05},
    {t: .19, both: book('in')}, {t: .21, both: book('up'), hold: .14},
  ]),
  READ: sign('r/read', 'V-fingertips scan down the upright non-dominant palm.', [.045, -.04].map((up, i) => ({
    t: i ? .32 : .3, hold: i ? .16 : 0, left: hand('FLAT', chest(.055, -.025, .36), 'up', 'back'),
    right: {shape: 'V', touch: {part: 'fingertips', on: 'palm', up, gap: .035}, fingers: ['forward', 'in'], palm: 'down'},
  }))),
  WRITE: sign('w/write', 'Pinched index and thumb scribble across the base palm.', [-.035, .045, -.015, .065].map((out, i) => ({
    t: i ? .18 : .3, hold: i === 3 ? .14 : 0, left: basePalm,
    right: {shape: 'WRITE_PINCH', touch: {part: 'indextip', on: 'palm', out, gap: .008}, fingers: ['down', 'in'], palm: 'back'},
  }))),
  GO: sign('g/go', 'Index hands move outward toward the destination.', [
    {t: .28, both: hand('1', chest(.12, .02, .23), 'up', 'in')},
    {t: .28, hold: .16, both: hand('1', chest(.19, -.01, .41), 'forward', 'down')},
  ]),
  COME: sign('c/come', 'Index hands move toward the signer.', [
    {t: .28, both: hand('1', chest(.18, .015, .4), ['back', 'up'], 'up')},
    {t: .28, hold: .16, both: hand('1', chest(.11, -.07, .19), ['back', 'down'], 'up')},
  ]),
  WAIT: sign('w/wait', 'Spread fingers flutter with hands held off to one side.', ['WAIT_A', 'WAIT_B', 'WAIT_A', 'WAIT_B'].map((shape, i) => ({
    t: i ? .17 : .3, hold: i === 3 ? .14 : 0,
    right: hand(shape, chest(.24, .01, .28), 'up', 'in'),
    left: hand(shape, chest(-.015, .01, .28), 'up', 'out'),
  }))),
  SIT: sign('s/sit', 'Bent paired fingers sit once across the base paired fingers.', [
    phase(sitHand(.08), .3, 0, {left: sitBase}), phase(sitHand(.012), .25, .17, {left: sitBase}),
  ]),
  STAND: sign('s/stand', 'Downward V-fingertips stand on the base palm.', [
    phase({shape: 'V', touch: {part: 'fingertips', on: 'palm', up: .1}, fingers: 'down', palm: 'back'}, .3, 0, {left: basePalm}),
    phase({shape: 'V', touch: {part: 'fingertips', on: 'palm', gap: .008}, fingers: 'down', palm: 'back'}, .25, .17, {left: basePalm}),
  ]),
  SLEEP: sign('s/sleep', 'Spread hand lowers before the face and closes into flattened O.', [
    phase(hand('5', at('nose', .015, -.01, .23)), .3),
    phase(hand('FLAT_O', at('chin', .015, -.05, .23)), .34, .16, {head: {nod: 7}}),
  ]),
  TIRED: sign('t/tired', 'Bent hands at the chest slump by rotating downward.', [
    {t: .3, both: hand('BENT_B', chest(.13, .015, .14), 'in', 'back')},
    {t: .32, hold: .16, both: hand('BENT_B', chest(.13, .015, .14), ['down', 'in'], 'down'), head: {nod: 8}},
  ]),
  HURT: sign('h/hurt', 'Two index fingertips jab toward each other twice.', [
    {t: .3, ...hurt(.2)}, {t: .18, ...hurt(.125), hold: .04},
    {t: .18, ...hurt(.2)}, {t: .18, ...hurt(.125), hold: .14},
  ]),
  SICK: sign('s/sick', 'Bent middle fingertips contact forehead and stomach together.', [
    {t: .3, face: 'concern', right: contact('OPEN_8', 'forehead', 'middletip', {forward: .08}), left: contact('OPEN_8', 'belly', 'middletip', {forward: .12})},
    {t: .28, hold: .17, face: 'concern', right: contact('OPEN_8', 'forehead', 'middletip', {forward: .015}), left: contact('OPEN_8', 'belly', 'middletip', {forward: .025})},
  ]),
});

// Only lexical variants/inflections, not arbitrary English synonyms.
export const EVERYDAY_ALIASES = Object.freeze({
  MOM: 'MOTHER', MOMMY: 'MOTHER', MUM: 'MOTHER', DAD: 'FATHER', DADDY: 'FATHER',
  YOURS: 'YOUR', NEEDS: 'NEED', NEEDED: 'NEED', NEEDING: 'NEED',
  LIKES: 'LIKE', LIKED: 'LIKE', LIKING: 'LIKE', EATS: 'EAT', EATING: 'EAT', ATE: 'EAT',
  DRINKS: 'DRINK', DRINKING: 'DRINK', DRANK: 'DRINK', RESTROOM: 'BATHROOM', TOILET: 'BATHROOM',
  BOOKS: 'BOOK', READING: 'READ', READS: 'READ', WRITES: 'WRITE', WRITING: 'WRITE', WROTE: 'WRITE',
  GOES: 'GO', GOING: 'GO', WENT: 'GO', COMES: 'COME', COMING: 'COME', CAME: 'COME',
  WAITS: 'WAIT', WAITING: 'WAIT', WAITED: 'WAIT', SITS: 'SIT', SITTING: 'SIT', SAT: 'SIT',
  STANDS: 'STAND', STANDING: 'STAND', STOOD: 'STAND', SLEEPS: 'SLEEP', SLEEPING: 'SLEEP', SLEPT: 'SLEEP',
  HURTS: 'HURT', PAIN: 'HURT', ILL: 'SICK', ILLNESS: 'SICK',
});
