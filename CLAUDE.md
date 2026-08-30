# golem — working rules

The Golem's Scroll: a Reagent puzzle game. The engine is `.cljc` and runs on both
platforms (`golem.core`, `golem.scroll`, `golem.levels`); `golem.ui` is `.cljs`.

    lein test        # JVM
    lein fig:test    # ClojureScript, incl. golem.ui

## 1. The minimum readable code the functionality needs

Write the least code that delivers the behaviour. Before adding anything to
`src/`, ask what actually breaks if it does not exist — if the answer is
"nothing", do not add it.

- **No unused surface.** Do not add a function, arity, option key or level of
  indirection to `src/` that has no caller in `src/` today. Not "for later",
  not "for symmetry", not "for flexibility".
- **Readable, not terse.** Minimum code is not minimum characters. This codebase
  explains *why* in prose comments and keeps functions small; match the comment
  density and naming of the file you are editing. A clear ten lines beats a
  clever four.
- **Deleting is a valid answer.** If a request is best served by removing code,
  say so and propose it. Do not add a second mechanism beside a first.

## 2. Tests consume the shipping code; they never grow it

A test may not add code to `src/`. If a test needs something that does not
exist, it is built in `test/`.

- **Never** add a helper, projection, extra arity or option to `src/` so that a
  test can assert on it. That code belongs in the test namespace that wants it.
- **Prefer the path the app takes.** `golem.ui` drives the engine with
  `init-state`, `step` and `running?` — a test that drives it the same way tests
  what actually ships. A test-only driver exercises test-only control flow.
- Fixtures, drivers, golden data and assertion helpers live in `test/`, even
  when they are three lines and would sit "nicely" next to the function under
  test. Duplicating a small helper across two test namespaces is preferable to
  hoisting it into `src/`.
- Shared test support — drivers, projections over a run, fixtures — may live in
  a non-test namespace under `test/`. `golem.replay` is that namespace: it runs
  a scroll to completion and answers questions about the run, which nothing in
  `src/` needs. Test namespaces require it; they do not require each other.

## 3. A multi-stage function reads as its own flow

**`golem.core/step` is the reference implementation. Read it before writing one
of these** — it is shorter and more precise than any description here.

The shape:

- The public function's body is the numbered flow and nothing else: one line per
  stage, each a call to a named private fn, with a trailing `; 1.` `; 2.` marker.
- Each stage is a `defn-` directly above it, whose docstring opens with its
  number. A reader who cares about stage 3 goes straight to it; one who does not
  reads five lines and stops.
- A stage that can end the flow early returns the finished value, or `nil` for
  "not my case, keep reading". `or` chains those.
- A stage that always produces a value threads with `->`.
- The prose that used to be an inline comment moves into the stage's docstring.
  Do not leave it at the call site as well.

**Apply it when** the body has three or more distinct phases, or no longer fits
on a screen.

**Do not apply it** to a function that does one thing. `walk`, `in-bounds?` and
the `golem.ui` components stay exactly as they are. Splitting a five-line
function into three two-line functions is worse, not better — and rule 1 still
governs: the stages must be the same logic with names on it, never new
machinery.

**Never fold a possibly-falsey value into the `or`.** `or` cannot distinguish
"the guard declined" from "the value is nil or false". In `step` this is why
stage 1 sits outside the chain: `nil` is a valid state (no game — the player is
editing), and `(when-not (running? nil) nil)` returns `nil` either way. A
comment at the site says so; keep it there.

**Do not build a dispatch mechanism.** A vector of stage vars reduced with
`reduced` is slower, wrecks stack traces, and earns nothing at this size. Write
the flow out literally.

## When these conflict with something else

Say so in one sentence and follow the rules. If a rule looks wrong for a
specific case, raise it and let the human decide — do not quietly make an
exception, and do not weaken a rule by adding a caveat to this file.
