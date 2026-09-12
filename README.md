# Migrating UI tests: legacy robot to Starter and Driver

The same five UI test scenarios, implemented twice.
Every difference between the two frameworks therefore points at two concrete lines of code.
Both sides drive IntelliJ IDEA 2026.1.

* `starter-driver/` uses [Starter and Driver](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html), the current framework.
* `legacy-robot/` uses [intellij-ui-test-robot](https://github.com/JetBrains/intellij-ui-test-robot), the older one, following the syntax of its [ui-test-example](https://github.com/JetBrains/intellij-ui-test-robot/tree/master/ui-test-example).

Both open the same `sample-project/`, do the same things and assert the same facts.
Every scenario file is commented step by step, and the tables below link straight to those steps.

## Contents

1. [How to run](#how-to-run)
2. [Layout](#layout)
3. [What changes at a glance](#what-changes-at-a-glance)
4. [S1. Launch, project, plugin, readiness](#s1-launch-project-plugin-readiness)
5. [S2. Wait for an event, do not sleep](#s2-wait-for-an-event-do-not-sleep)
6. [S3. Pick an entry that only a predicate can identify](#s3-pick-an-entry-that-only-a-predicate-can-identify)
7. [S4. Read IDE state without test code in src/main](#s4-read-ide-state-without-test-code-in-srcmain)
8. [S5. A hand-written page object](#s5-a-hand-written-page-object)

## How to run

These are real UI tests: a real IDE, driven by a real mouse.
They need a desktop session, and the machine has to be left alone while they run, because whatever window is in front receives the clicks.
On macOS the terminal also needs Accessibility permission.
Gradle downloads the JDK and the IDE itself.

> Without Accessibility permission the mouse never moves, every click lands nowhere, and the tests fail on waits that look unrelated.

The Starter side is one command.
It downloads the IDE, starts it, runs the test and shuts it down.

```bash
./gradlew :starter-driver:integrationTest
```

The legacy side is two commands in two terminals, because the IDE is a separate process that outlives the tests.

```bash
# terminal 1, starts an IDE with the robot-server plugin listening on port 8082
./gradlew :legacy-robot:runIdeForUiTests

# terminal 2, once the IDE window is up
./gradlew :legacy-robot:test
```

## Layout

```text
migration-guide/
  sample-project/                     the project both sides open
  starter-driver/
    src/integrationTest/kotlin/.../starter/
      StarterScenarioTest.kt          shared setup, this is step 1 of S1
      S1LaunchAndReadinessTest.kt     one class per scenario
      S2WaitForEventTest.kt
      S3PredicateOnlyTest.kt
      S4RemoteStateTest.kt
      S5PageObjectTest.kt
      pages/ProjectPanelUi.kt         the page object used by S5
  legacy-robot/
    src/test/kotlin/.../legacy/
      LegacyScenarioTest.kt           shared setup, and the cleanup Starter does not need
      S1LaunchAndReadinessTest.kt     the same five scenarios
      S2WaitForEventTest.kt
      S3PredicateOnlyTest.kt
      S4RemoteStateTest.kt
      S5PageObjectTest.kt
      pages/                          IdeaFrame, DialogFixture, ActionMenuFixture, ProjectPanelFixture
      utils/                          RemoteRobotExtension, StepsLogger
```

## What changes at a glance

| | Starter and Driver | Legacy robot |
|---|---|---|
| Who starts the IDE | the test | a Gradle task, in another terminal |
| Commands to run one test | one | two |
| Cleanup between tests | the IDE is thrown away | the test closes the project and waits for it to be gone |
| Reading IDE state | typed `@Remote` interface | a JavaScript string sent to `callJs()` |
| Component locators | typed query builder, `byType()`, `byClass()` | raw xpath strings |
| Waiting | `waitFor()`, `waitForOne()`, `shouldBe()`, `waitForIndicators()` | `waitFor()` and `waitForIgnoringError()`, everything else by hand |
| Invoking an action by id | `invokeAction()`, which takes the context component and the `now` flag, and it checks the result | `CommonSteps.invokeAction()` for anything that does not open a popup, a JavaScript string otherwise |
| Artifacts on failure | screenshots and logs per test, out of the box | written by the test author |
| Settings that make an IDE testable | Starter's own defaults | a hand-kept list of `-D` flags |


## S1. Launch, project, plugin, readiness

Starter: [S1LaunchAndReadinessTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt) · Legacy: [S1LaunchAndReadinessTest.kt](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt)

| Step | Starter and Driver | Legacy robot | What changed |
|---|---|---|---|
| 1. Build the context | [L23](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L23) | [L22](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L22) | On the Starter side the product, the version, the project and the plugin are values in Kotlin. On the legacy side there is no step 1: it lives in `build.gradle.kts`, outside anything the test can read or assert. |
| 2. Start the IDE, open the project | [L26](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L26) | [L24](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L24) | Starter installs the plugin, drops `.idea` and every `.iml`, starts the IDE and shuts it down when the test finishes. Legacy can only open a project, and it opens whatever `.idea` happens to be on disk. |
| 3. Is the plugin loaded | [L29](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L29) | [L27](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L27) | `isPluginLoaded()` answers with a typed boolean. The legacy equivalent is a JavaScript string over `PluginManagerCore`, written and maintained in the test. |
| 4. Wait for readiness | [L32](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L32) | [L30](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L30) | `waitForIndicators()` covers indexing and every background progress bar. Legacy has only dumb mode, the flag that stays on while the IDE builds its indexes, and it reads that flag through another JavaScript snippet. |
| 5. A project is open | [L36](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L36) | [L35](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L35) | Same idea. `project` is a typed handle versus a JS call returning the project name. |
| 6. Open the Project View | [L39](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L39) | [L39](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L39) | Driver calls `projectButton.open()`, which invokes the action behind the tool window. The legacy library cannot do that: `CommonSteps.invokeAction()` gives the action no component to read the project from, and it reports nothing when the action then does nothing, so the test clicks the Project button on the left toolbar instead. That button is a toggle while `open()` is not, so the legacy side has to keep clicking until the panel is really open. Driver can also ask the tree whether its nodes are loaded with `waitForNodesLoaded()`. Legacy keeps reading the tree until rows appear. |
| 7. Expand the source folder | [L45](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L45) | [L68](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L68) | The root node carries two pieces of text, the project name and its location on disk, and both sides read them back as one label: `sample-project ~/IdeaProjects`. Driver `expandPath()` takes `fullMatch = false`, so `sample-project` still matches. Legacy `expand(vararg path)` compares labels exactly and has no `fullMatch` variant, so expansion has to go through a double click. |
| 8. Both files are in the tree | [L50](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L50) | [L83](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L83) | `findExpandedPath()` compared against null versus `isPathExists()`. Neither of them changes the tree. Driver's `pathExists()` expands first, which would have made this pass whether or not step 7 ran. |
| 9. Open the file by double click | [L55](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L55) | [L88](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L88) | Identical, `doubleClickPath()` on both sides. |
| 10. The right file opened | [L59](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L59) | [L91](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L91) | Driver asks the editor tabs directly, `editorTabs().isTabOpened(...)` and `codeEditorForFile(...)`. The legacy library has nothing for editor tabs, so the test takes the open editor and asks it which file it shows, which is one more JavaScript call into the IDE. |
| 11. Collapse and compare | [L64](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L64) | [L103](legacy-robot/src/test/kotlin/com/example/migration/legacy/S1LaunchAndReadinessTest.kt#L103) | Identical: `collapsePath()`, then a wait for the row count to drop. Neither side settles by itself. Driver's `expandPath()` waits internally and `collapsePath()` does not, so the wait is easy to leave out. |

## S2. Wait for an event, do not sleep

Starter: [S2WaitForEventTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt) · Legacy: [S2WaitForEventTest.kt](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt)

| Step | Starter and Driver | Legacy robot | What changed |
|---|---|---|---|
| 1. Invoke the action by id | [L24](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L24) | [L25](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L25) | Both avoid keyboard shortcuts, which depend on the OS and the keymap. `invokeAction()`, which takes the context component and the `now` flag, versus a JavaScript string in `pages/IdeaFrame.kt`: `CommonSteps.invokeAction()` hardcodes `now = true`, never opens the Search Everywhere popup, and reports nothing. |
| 2. Switch to the Actions tab | [L29](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L29) | [L56](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L56) | Driver has `selectTab()` and reads the answer back with a typed `getSelectedTab()`. On the legacy side the tab is a piece of rendered text to click, and the only way to ask which tab won is another JavaScript call. The step exists because on the All tab the query also matches the contents of files, and such a result is clickable exactly like the action. |
| 3. Type the query | [L37](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L37) | [L70](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L70) | Driver has a typed `searchField` on the popup. The legacy side sets the text on the search field directly, because typing would mean real key presses. Those go wherever the focus is, and the OS decodes them through whatever keyboard layout happens to be active. |
| 4. Wait for the results | [L40](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L40) | [L75](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L75) | `shouldBe()` wraps `waitFor()`, defaults to 15 seconds, and uses its first argument as the text of the timeout error. Legacy has to spell out `waitFor()` and pass a `description` to get the same message. Neither side should ever use `Thread.sleep()`. |
| 5. Click the result by text | [L45](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L45) | [L81](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L81) | Almost identical. By text on both sides, never by position in the list. |
| 6. Close the dialog with a button | [L49](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L49) | [L84](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L84) | Same on both sides, and both had to stop using Escape: the Settings dialog swallows it. |
| 7. The dialog really closed | [L55](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L55) | [L87](legacy-robot/src/test/kotlin/com/example/migration/legacy/S2WaitForEventTest.kt#L87) | Driver keeps the handle on the dialog it just clicked in, and `shouldBe { notPresent() }` re-resolves that same locator until nothing is found. The legacy side has no handle to reuse, so it spells the title locator out a second time and checks that `findAll()` comes back empty. Driver's generic `waitForNoOpenedDialogs()` is not the answer here: it looks for `//div[@class='MyDialog']`, and the Settings window is not one, so it would report success without checking anything. |

## S3. Pick an entry that only a predicate can identify

Starter: [S3PredicateOnlyTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt) · Legacy: [S3PredicateOnlyTest.kt](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt)

| Step | Starter and Driver | Legacy robot | What changed |
|---|---|---|---|
| 1. Open the context menu | [L32](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L32) | [L38](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L38) | Same right click. Legacy also opens the file, waits for the editor and raises the window; Driver needs none of it. |
| 2. Take the popup | [L35](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L35) | [L42](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L42) | Driver takes the popup as an object with `popupMenu()` and searches inside it. Legacy has no such object: after the right click the entries are separate top level components, so every later search starts from `RemoteRobot` and goes over all open windows. |
| 3. Describe the entries as two collections | [L38](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L38) | [L42](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L42) | `xx()` with a typed query versus a raw xpath string. Driver also ships `PopupItemUiComponent`, which reads a label with `getText()`. The library has nothing for a menu entry, so `pages/ActionMenuFixture.kt` wraps one and reads the label through `callJs()`. That page object is why the legacy column points at the same line twice. |
| 4. Poll the submenus with a predicate | [L44](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L44) | [L48](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L48) | `waitForOne()` versus a retry loop plus `single()`. |
| 5. Click it | [L55](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L55) | [L57](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L57) | Identical. |
| 6. The submenu really opened | [L60](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L60) | [L60](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L60) | The check is the same on both sides: after the click there must be more menu entries on screen than before. To get that number the test asks the IDE for the list of entries, and on the legacy side this request throws while the menu is still redrawing, so its wait is `waitForIgnoringError()`. Plain `waitFor()` does not retry after an error, on either side. The Driver wait that retries is `shouldBe()`. |
| 7. Close the submenu | [L65](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L65) | [L65](legacy-robot/src/test/kotlin/com/example/migration/legacy/S3PredicateOnlyTest.kt#L65) | Identical. |

## S4. Read IDE state without test code in src/main

Starter: [S4RemoteStateTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt) · Legacy: [S4RemoteStateTest.kt](legacy-robot/src/test/kotlin/com/example/migration/legacy/S4RemoteStateTest.kt)

| Step | Starter and Driver | Legacy robot | What changed |
|---|---|---|---|
| 1. Declare the IDE class | [L21](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L21) | [L20](legacy-robot/src/test/kotlin/com/example/migration/legacy/S4RemoteStateTest.kt#L20) | Driver declares a typed `@Remote` interface naming the IntelliJ Platform class and only the methods the test needs, and it lives in the test source set. Legacy has no such thing: the call is a JavaScript string in `pages/IdeaFrame.kt`. Nothing compiles it, nothing checks the class names, and a rename in the IntelliJ Platform shows up as a runtime stack trace from the script engine. |
| 2. Get the project | [L38](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L38) | [L20](legacy-robot/src/test/kotlin/com/example/migration/legacy/S4RemoteStateTest.kt#L20) | `singleProject()` returns the open project. The legacy JavaScript has to find the IDE window first and take the project from it. |
| 3. Get the service | [L41](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L41) | [L20](legacy-robot/src/test/kotlin/com/example/migration/legacy/S4RemoteStateTest.kt#L20) | Driver looks the service up with `service<ToolWindowManagerRef>(project)` and gets a typed object back. The legacy side has no separate step here, because the project, the service and the call are one JavaScript string. That is why its column points at the same line for steps 1 to 3. |
| 4. Click in the UI | [L47](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L47) | [L24](legacy-robot/src/test/kotlin/com/example/migration/legacy/S4RemoteStateTest.kt#L24) | A real click on both sides. On the Driver side `open()` would call `invokeAction()` internally and make the scenario pointless, so `click()` is used. |
| 5. Read the state back | [L50](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L50) | [L27](legacy-robot/src/test/kotlin/com/example/migration/legacy/S4RemoteStateTest.kt#L27) | Both wait for the state to change instead of sleeping. The difference is that one side is typed and refactorable and the other is a code string. |

## S5. A hand-written page object

Starter: [pages/ProjectPanelUi.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt) and [S5PageObjectTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S5PageObjectTest.kt)  
Legacy: [pages/ProjectPanelFixture.kt](legacy-robot/src/test/kotlin/com/example/migration/legacy/pages/ProjectPanelFixture.kt) and [S5PageObjectTest.kt](legacy-robot/src/test/kotlin/com/example/migration/legacy/S5PageObjectTest.kt)

| Step | Starter and Driver | Legacy robot | What changed |
|---|---|---|---|
| 1. The wrapper class | [L8](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L8) | [L19](legacy-robot/src/test/kotlin/com/example/migration/legacy/pages/ProjectPanelFixture.kt#L19) | Both sides subclass a base class from the framework, and the test calls neither constructor. The lookup function in step 4 creates the object and passes in whatever the base class requires, so this line is boilerplate on both sides. |
| 2. A single child | [L11](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L11) | [L28](legacy-robot/src/test/kotlin/com/example/migration/legacy/pages/ProjectPanelFixture.kt#L28) | `val tree = x { byType("...") }` versus `jTree(byXpath("...", "..."))`. Both wait on their own. |
| 3. A collection of children | [L15](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L15) | [L32](legacy-robot/src/test/kotlin/com/example/migration/legacy/pages/ProjectPanelFixture.kt#L32) | `xx { }.list()` versus `findAll(...)`. Neither waits, so the wrapper exposes the collection and lets the caller decide how long to wait. |
| 4. How the wrapper is found | [L22](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L22) | [L37](legacy-robot/src/test/kotlin/com/example/migration/legacy/pages/ProjectPanelFixture.kt#L37) | A function returning `x(MyUi::class.java) { }` with a typed query, versus a `@DefaultXpath` annotation holding a raw xpath string that nothing checks until the test runs. |
| 5. Use the wrapper | [L22](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S5PageObjectTest.kt#L22) | [L27](legacy-robot/src/test/kotlin/com/example/migration/legacy/S5PageObjectTest.kt#L27) | Same shape on both sides. |
| 6. Assert through it | [L25](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S5PageObjectTest.kt#L25) | [L30](legacy-robot/src/test/kotlin/com/example/migration/legacy/S5PageObjectTest.kt#L30) | Driver has `shouldBe()` and `hasSubtext()`. Legacy collects the rows and searches them by hand inside a `waitForIgnoringError()`. |
