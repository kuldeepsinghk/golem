# golem

FIXME: Write a one-line description of your library/project.

## Overview

FIXME: Write a paragraph about the library/project and highlight its goals.

## Development

To get an interactive development environment run:

    lein fig:build

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

	lein clean

To create a production build run:

	lein clean
	lein fig:min


## Tests and coverage

The engine lives in `.cljc`, so it runs on both platforms and is tested on both:

	lein test        # JVM — golem.core, golem.scroll, golem.levels
	lein fig:test    # ClojureScript — the above plus golem.ui

To see which lines the tests actually execute:

	lein cov

That writes `target/coverage/index.html`: a page per file with every line
marked covered, not-covered, or partial (some forms on the line ran, some
did not). Use it to find untested branches, not as a score.

Coverage is JVM-only, so it measures the `.cljc` engine and reports nothing
for `golem.ui` — that namespace is covered only in the sense that
`lein fig:test` passes.

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
