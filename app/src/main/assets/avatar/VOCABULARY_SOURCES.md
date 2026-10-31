# Live-caption vocabulary

The Android runtime enables every entry of `vendor/sign-dictionary.js` through
`common-signs.js`: 64 citation-form entries, plus lexical variants in
`SIGNABLE_WORDS`. Unknown words and names still fingerspell. The separate
`avatar/dist` studio is an upstream reference, not the Android runtime.

`everyday-signs.js` adds 32 authored procedural motions. Its `source` fields link
to the ASL University / Lifeprint lessons consulted for handshape, location,
orientation, movement, and repetition. Definitions use original numerical
parameters; no instructional videos, images, or motion-capture data are bundled.

| Concepts | References |
| --- | --- |
| Water; eat / food; drink | [Water](https://www.lifeprint.com/asl101/pages-signs/w/water.htm), [Eat / food](https://www.lifeprint.com/asl101/pages-signs/e/eat.htm), [Drink](https://www.lifeprint.com/asl101/pages-signs/d/drink.htm) |
| Bathroom; need; like | [Bathroom](https://www.lifeprint.com/asl101/pages-signs/b/bathroom.htm), [Need](https://www.lifeprint.com/asl101/pages-signs/n/need.htm), [Like](https://www.lifeprint.com/asl101/pages-signs/l/like.htm) |
| My / mine; your | [My / mine](https://www.lifeprint.com/asl101/pages-signs/m/my.htm), [Your](https://www.lifeprint.com/asl101/pages-signs/y/your.htm) |
| Mother; father; family | [Mother](https://www.lifeprint.com/asl101/pages-signs/m/mom.htm), [Father](https://www.lifeprint.com/asl101/pages-signs/d/dad.htm), [Family](https://www.lifeprint.com/asl101/pages-signs/f/family.htm) |
| Where; who; when; why; how | [Where](https://www.lifeprint.com/asl101/pages-signs/w/where.htm), [Who](https://www.lifeprint.com/asl101/pages-signs/w/who.htm), [When](https://www.lifeprint.com/asl101/pages-signs/w/when.htm), [Why](https://www.lifeprint.com/asl101/pages-signs/w/why.htm), [How](https://www.lifeprint.com/asl101/pages-signs/h/how.htm) |
| Home; school; book; read; write | [Home](https://www.lifeprint.com/asl101/pages-signs/h/home.htm), [School](https://www.lifeprint.com/asl101/pages-signs/s/school.htm), [Book](https://www.lifeprint.com/asl101/pages-signs/b/book.htm), [Read](https://www.lifeprint.com/asl101/pages-signs/r/read.htm), [Write](https://www.lifeprint.com/asl101/pages-signs/w/write.htm) |
| Go; come; wait; sit; stand | [Go](https://www.lifeprint.com/asl101/pages-signs/g/go.htm), [Come](https://www.lifeprint.com/asl101/pages-signs/c/come.htm), [Wait](https://www.lifeprint.com/asl101/pages-signs/w/wait.htm), [Sit](https://www.lifeprint.com/asl101/pages-signs/s/sit.htm), [Stand](https://www.lifeprint.com/asl101/pages-signs/s/stand.htm) |
| Sleep; tired; hurt; sick | [Sleep](https://www.lifeprint.com/asl101/pages-signs/s/sleep.htm), [Tired](https://www.lifeprint.com/asl101/pages-signs/t/tired.htm), [Hurt](https://www.lifeprint.com/asl101/pages-signs/h/hurt.htm), [Sick](https://www.lifeprint.com/asl101/pages-signs/s/sick.htm) |

The existing YALL entry now uses an index sweep, following
[ASLU indexing](https://www.lifeprint.com/asl101/pages-layout/indexing.htm).
The previous aliases LIKE→GOOD, NEED→WANT, MY/MINE→ME were replaced with distinct
motions. WATCH/LOOK→SEE, GREAT→GOOD, OK→YES, and EVERYONE/EVERYBODY→YALL were
removed because those meanings are not interchangeable. English inflections
resolve to a citation form; they do not encode tense or ASL aspect.

## Scope and verification

These are experimental avatar approximations, not Deaf-signer-validated
translations. The word-by-word pipeline retains English order and does not
resolve word senses (for example, LIKE means liking, not similarity), spatial
referents, or sentence-level ASL grammar. Facial markers depend on the avatar's
available facial rig. More vocabulary does not establish linguistic accuracy.

`scripts/test_avatar_vocabulary.mjs` compiles all entries and variants, checks
finite moving tracks, handshape coverage, timing, distinct meanings, two-hand
participation, and fallback spelling. The WebGL smoke test exercises the real
model and bridge. Android instrumentation covers recognized caption offsets,
partial/final deduplication, rendered movement, and continuous handoffs.
