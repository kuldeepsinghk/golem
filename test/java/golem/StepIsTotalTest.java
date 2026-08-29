package golem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.ILookup;
import clojure.lang.Keyword;
import clojure.lang.RT;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Java mirror of golem.core-test/step-is-total.
 *
 * <p>A reading aid, not a second suite: the Clojure tests are the ones that
 * must pass and the only ones that run on both platforms. This mirrors a single
 * Clojure test so the two can be read side by side. Run it from IntelliJ like
 * any JUnit class; `lein test` runs it too, through golem.java-test.
 *
 * <p>Reading the Clojure it mirrors: the first symbol in a list is the verb, so
 * {@code (f x)} is {@code f(x)} and nested calls read inside-out. Nothing
 * mutates — every engine call returns a new state, which is why comparing whole
 * maps with equals() is a meaningful assertion.
 */
@DisplayName("step-is-total")
class StepIsTotalTest {

    // Clojure names are resolved as vars. The require must happen before any
    // golem.core var is looked up, so this block sits above those fields.
    private static final IFn REQUIRE = Clojure.var("clojure.core", "require");
    static { REQUIRE.invoke(Clojure.read("golem.core")); }

    private static final IFn INIT_STATE = Clojure.var("golem.core", "init-state");
    private static final IFn STEP       = Clojure.var("golem.core", "step");
    private static final IFn TRACE      = Clojure.var("golem.core", "trace");
    private static final IFn PEEK       = Clojure.var("clojure.core", "peek");

    /** :some-keyword — an interned constant; the nearest Java analogue is an enum value. */
    private static Keyword kw(String name) { return Keyword.intern(name); }

    /** (:status state) — a map lookup by keyword. */
    private static Object get(Object map, String key) {
        return ((ILookup) map).valAt(kw(key));
    }

    /** {:start [0 0] :dir :east :gem [6 5]} — core-test's fixture-level. */
    private static final Object FIXTURE_LEVEL = RT.map(
        kw("start"), RT.vector(0, 0),
        kw("dir"),   kw("east"),
        kw("gem"),   RT.vector(6, 5));

    /** (peek (trace (init-state level scroll opts))) — run a scroll to its end. */
    private static Object runToEnd(Object level, Object scroll, Object opts) {
        return PEEK.invoke(TRACE.invoke(INIT_STATE.invoke(level, scroll, opts)));
    }

    /** The Clojure destructures each row into [scroll expected]; JUnit passes them as arguments. */
    static Stream<Arguments> terminalScrolls() {
        return Stream.of(
            Arguments.of(RT.vector(kw("walk")),             kw("empty")),
            Arguments.of(RT.vector(kw("left"), kw("walk")), kw("crashed")),
            Arguments.of(RT.vector(kw("echo"), kw("echo")), kw("exhausted")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("terminalScrolls")
    @DisplayName("every terminal status is a fixed point")
    void everyTerminalStatusIsAFixedPoint(Object scroll, Object expected) {
        Object opts = RT.map(kw("max-steps"), 8);          // {:max-steps 8}
        Object done = runToEnd(FIXTURE_LEVEL, scroll, opts);

        // (is (= expected (:status done))) — pin the premise, so the test cannot
        // pass by asserting the fixed-point property about the wrong state.
        assertEquals(expected, get(done, "status"), "did not reach " + expected);

        // (is (= done (g/step done))) — the claim. Whole-map equality is the
        // point here: nothing at all may change.
        assertEquals(done, STEP.invoke(done), "stepping a terminal state changed it");
    }

    @Test
    @DisplayName("a won game is a fixed point too — the gem cannot be walked off")
    void wonGameIsAFixedPoint() {
        Object gemNextDoor = RT.map(
            kw("start"), RT.vector(0, 0),
            kw("dir"),   kw("east"),
            kw("gem"),   RT.vector(1, 0));
        Object won = runToEnd(gemNextDoor, RT.vector(kw("walk")), null);

        assertEquals(kw("won"), get(won, "status"), "did not reach :won");
        assertEquals(won, STEP.invoke(won), "stepping a won game changed it");
    }

    @Test
    @DisplayName("no game at all steps to no game — a stale UI tick cannot throw")
    void nilIsAFixedPoint() {
        assertNull(STEP.invoke((Object) null));
    }
}
