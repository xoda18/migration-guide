# Migrating UI tests: Remote Robot to Starter and Driver

The same five UI test scenarios, implemented twice.
Every difference between the two frameworks therefore points at two concrete lines of code.
Both sides drive IntelliJ IDEA 2026.1.

* `starter-driver/` uses [Starter and Driver](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html), the current framework.
* `remote-robot/` uses [intellij-ui-test-robot](https://github.com/JetBrains/intellij-ui-test-robot), the older one, following the syntax of its [ui-test-example](https://github.com/JetBrains/intellij-ui-test-robot/tree/master/ui-test-example).

Both open the same project, [sample-project](https://github.com/xoda18/sample-project), do the same things and assert the same facts. Nothing to clone by hand: Starter checks the project out itself, and for the Remote Robot side Gradle downloads it.
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

The Remote Robot side is two commands in two terminals, because the IDE is a separate process that outlives the tests.

```bash
# terminal 1, starts an IDE with the robot-server plugin listening on port 8082
./gradlew :remote-robot:runIdeForUiTests

# terminal 2, once the IDE window is up
./gradlew :remote-robot:test
```

## Layout

```text
migration-guide/
  build/sample-project/               the project, downloaded by Gradle for the Remote Robot side
  starter-driver/
    src/integrationTest/kotlin/.../starter/
      StarterScenarioTest.kt          shared setup, this is step 1 of S1
      S1LaunchAndReadinessTest.kt     one class per scenario
      S2WaitForEventTest.kt
      S3PredicateOnlyTest.kt
      S4RemoteStateTest.kt
      S5PageObjectTest.kt
      pages/ProjectPanelUi.kt         the page object used by S5
  remote-robot/
    src/test/kotlin/.../remoterobot/
      RemoteRobotScenarioTest.kt           shared setup, and the cleanup Starter does not need
      S1LaunchAndReadinessTest.kt     the same five scenarios
      S2WaitForEventTest.kt
      S3PredicateOnlyTest.kt
      S4RemoteStateTest.kt
      S5PageObjectTest.kt
      pages/                          IdeaFrame, DialogFixture, ActionMenuFixture, ProjectPanelFixture
      utils/                          RemoteRobotExtension, StepsLogger
```

## What changes at a glance

| | Starter and Driver | Remote Robot |
|---|---|---|
| Who starts the IDE | The test | A Gradle task, in another terminal |
| Commands to run one test | One | Two |
| Cleanup between tests | The IDE is thrown away | The test closes the project and waits for it to be gone |
| Reading IDE state | Typed `@Remote` interface | A JavaScript string sent to `callJs()` |
| Component locators | Typed query builder, `byType()`, `byClass()` | Raw xpath strings |
| Waiting | `waitFor()`, `waitForOne()`, `shouldBe()`, `waitForIndicators()` | `waitFor()`, `waitForIgnoringError()`, and the library's ready-made steps: `CommonSteps.waitForSmartMode()` for indexing, `CommonSteps.wait(seconds)`, which is `Thread.sleep()` with a step annotation on it |
| Invoking an action by id | One call, and it reports whether the action ran | The ready-made call fails silently on anything that opens a popup, so those need hand-written JavaScript |
| Artifacts on failure | Screenshots and logs per test, out of the box | Written by the test author |
| Settings that make an IDE testable | Starter's own defaults | A hand-kept list of `-D` flags |


## S1. Launch, project, plugin, readiness

Starter: [S1LaunchAndReadinessTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt) · Remote Robot: [S1LaunchAndReadinessTest.kt](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt)

| Step | Starter and Driver | Remote Robot | What changed |
|---|---|---|---|
| 1. Build the context | [L23](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L23) | [L22](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L22) | On the Starter side the product, the version, the project and the plugin are values in Kotlin, and `GitHubProject.fromGithub()` checks the project out on its own. On the Remote Robot side there is no step 1: all of it lives in `build.gradle.kts`, outside anything the test can read or assert, and the project has to be on disk before the test starts. |
| 2. Start the IDE, open the project | [L26](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L26) | [L24](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L24) | Starter installs the plugin, drops `.idea` and every `.iml`, starts the IDE and shuts it down when the test finishes. Remote Robot can only open a project, and it opens whatever `.idea` happens to be on disk. |
| 3. Is the plugin loaded | [L29](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L29) | [L27](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L27) | `isPluginLoaded()` answers with a typed boolean. The Remote Robot equivalent is a JavaScript string over `PluginManagerCore`, written and maintained in the test. |
| 4. Wait for readiness | [L32](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L32) | [L30](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L30) | Driver has one call for it, `waitForIndicators()`: indexing, every progress bar in the status bar, and ten quiet seconds in a row. Remote Robot has `CommonSteps.waitForSmartMode()`, which covers indexing and nothing else, so each scenario below waits for its own component by hand. |
| 5. A project is open | [L36](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L36) | [L35](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L35) | Same idea. `project` is a typed handle versus a JS call returning the project name. |
| 6. Open the Project View | [L39](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L39) | [L39](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L39) | Driver opens the panel with `projectButton.open()`. Remote Robot cannot use the action id: `CommonSteps.invokeAction()` does not tell the action which project to act on, and it fails silently. So the test clicks the button, and checks the state first because a button toggles. For the tree: `waitForNodesLoaded()` versus reading rows until some appear. |
| 7. Expand the source folder | [L45](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L45) | [L58](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L58) | The root node reads back as one label on both sides, `sample-project ~/IdeaProjects`. Driver's `expandPath()` takes `fullMatch = false`, so `sample-project` still matches. Remote Robot's `expand(vararg path)` compares labels exactly and has no `fullMatch`, so expanding goes through a double click. |
| 8. Both files are in the tree | [L50](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L50) | [L71](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L71) | Both sides do the same check here, and neither call changes the tree. But be careful with the name: `isPathExists()` (Remote Robot) looks like `pathExists()` in the Driver API, and they are not the same. `pathExists()` expands the path before it checks, so the test would pass even if step 7 did nothing. This is why the starter side calls `findExpandedPath() != null` instead: it only reads the tree. |
| 9. Open the file by double click | [L55](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L55) | [L76](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L76) | Identical, `doubleClickPath()` on both sides. |
| 10. The right file opened | [L59](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L59) | [L79](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L79) | The starter side can ask the editor tabs: `codeEditorForFile(...)` finds the editor for a file, and `editorTabs().isTabOpened(...)` checks that the tab is open. The Remote Robot library has no fixture for editor tabs. So the test takes the open editor with `textEditor()` and reads its `fileName`, and that is one more JavaScript call into the IDE. |
| 11. Collapse and compare | [L64](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S1LaunchAndReadinessTest.kt#L64) | [L89](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S1LaunchAndReadinessTest.kt#L89) | Identical: `collapsePath()`, then a wait for the row count to drop. Neither side settles by itself. Driver's `expandPath()` waits internally and `collapsePath()` does not, so the wait is easy to leave out. |

## S2. Wait for an event, do not sleep

Starter: [S2WaitForEventTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt) · Remote Robot: [S2WaitForEventTest.kt](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt)

| Step | Starter and Driver | Remote Robot | What changed |
|---|---|---|---|
| 1. Invoke the action by id | [L24](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L24) | [L26](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L26) | `now` decides when the action runs. With `true` it runs inside the call, while the focus is still moving, and a popup opened then is built and dropped before it is ever shown. With `false` it is queued like a real key press and the popup stays. `CommonSteps.invokeAction()` fixes `true`, so Search Everywhere never appears, and it discards the `ActionCallback`, so nothing says why. Hence the hand-written `invokeAction()` in `pages/IdeaFrame.kt`, and the loop that calls it again. |
| 2. Switch to the Actions tab | [L29](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L29) | [L54](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L54) | `selectTab()` clicks the tab by its visible text, and the Remote Robot side does the same by hand. The difference is reading the tab back: Driver compares `getSelectedTabID()` with the `SearchEverywhereTab` enum, the Remote Robot side makes the same call through `callJs()` and compares it with the contributor id as a plain string. |
| 3. Type the query | [L35](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L35) | [L64](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L64) | Identical. |
| 4. Wait for the results | [L39](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L39) | [L69](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L69) | `shouldBe()` catches an exception from the condition and tries again. The robot library splits that in two: `waitForIgnoringError()` catches, plain `waitFor()` fails on the first exception. |
| 5. Click the result by text | [L44](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L44) | [L75](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L75) | Identical. |
| 6. Close the dialog with a button | [L48](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L48) | [L78](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L78) | Same on both sides. |
| 7. The dialog really closed | [L54](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S2WaitForEventTest.kt#L54) | [L81](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S2WaitForEventTest.kt#L81) | Driver reuses the dialog it just clicked in: `shouldBe { notPresent() }` re-runs the same locator until nothing is found. Remote Robot has no handle to reuse, so it writes the title locator a second time and checks that `findAll()` is empty. Driver's `waitForNoOpenedDialogs()` is a trap here: it looks for `//div[@class='MyDialog']`, which the Settings window is not, so it would pass without checking anything. |

## S3. Pick an entry that only a predicate can identify

Starter: [S3PredicateOnlyTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt) · Remote Robot: [S3PredicateOnlyTest.kt](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt)

| Step | Starter and Driver | Remote Robot | What changed                                                                                                                                                                                                                                                                                                                                                                                                                      |
|---|---|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. Open the context menu | [L32](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L32) | [L37](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L37) | Both sides open the file first, and the right click is the same. The difference is what comes with it: Driver's `openFile()` also waits until the file is analysed, so one call is enough. The Remote Robot `openFile()` in `pages/IdeaFrame.kt` only posts the request, so the test waits for the editor itself.                                                                                                                       |
| 2. Take the popup | [L35](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L35) | [L41](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L41) | Driver takes the popup as an object with `popupMenu()` and searches inside it. Remote Robot has no such object: after the right click the entries are separate top level components, so every later search starts from `RemoteRobot` and goes over all open windows.                                                                                                                                                                    |
| 3. Describe the entries as two collections | [L38](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L38) | [L41](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L41) | Driver ships `PopupItemUiComponent`, which reads a label with `getText()`. `RemoteRobot` has nothing for a menu entry, so `pages/ActionMenuFixture.kt` writes the wrapper, the xpath and the label getter, and that getter is one more `callJs()`. The Remote Robot column points at the same line for steps 2 and 3 because that page object holds both.                                                                                 |
| 4. Poll the submenus with a predicate | [L44](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L44) | [L45](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L45) | `waitForOne()` returns only when exactly one element matches, and it hands that element back. This is what makes the predicate safe here, because two entries contain the word Paste. The Remote Robot side builds the same thing by hand: `count { } == 1` inside the wait, then `single { }` after it. The wait gives back only a boolean, so the search runs a second time and can see a different menu than the wait did.           |
| 5. Click it | [L55](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L55) | [L54](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L54) | Identical.                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 6. The submenu really opened | [L60](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L60) | [L57](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L57) | Both sides check that there are more menu entries on screen than before. On the Remote Robot side reading that number throws while the menu is still redrawing, so the wait has to be `waitForIgnoringError()`. Driver's `xx().list()` returns an empty list instead of throwing, so a plain `waitFor()` is enough. Its error-tolerant wait, `shouldBe()`, is not an option here anyway: it only works on a component, not on a number. |
| 7. Close the submenu | [L65](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S3PredicateOnlyTest.kt#L65) | [L62](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S3PredicateOnlyTest.kt#L62) | Identical.                                                                                                                                                                                                                                                                                                                                                                                                                        |

## S4. Read IDE state without test code in src/main

Starter: [S4RemoteStateTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt) · Remote Robot: [S4RemoteStateTest.kt](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S4RemoteStateTest.kt)

| Step | Starter and Driver | Remote Robot | What changed |
|---|---|---|---|
| 1. Declare the IDE class | [L21](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L21) | [L20](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S4RemoteStateTest.kt#L20) | Driver declares a typed `@Remote` interface naming the IntelliJ Platform class and only the methods the test needs, and it lives in the test source set. Remote Robot has no such thing: the call is a JavaScript string in `pages/IdeaFrame.kt`. Nothing compiles it, nothing checks the class names. |
| 2. Get the project | [L38](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L38) | [L20](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S4RemoteStateTest.kt#L20) | `singleProject()` returns the open project. The Remote Robot JavaScript has to find the IDE window first and take the project from it. |
| 3. Get the service | [L41](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L41) | [L20](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S4RemoteStateTest.kt#L20) | Driver looks the service up with `service<ToolWindowManagerRef>(project)` and gets a typed object back. The Remote Robot side has no separate step here, because the project, the service and the call are one JavaScript string. That is why its column points at the same line for steps 1 to 3. |
| 4. Click in the UI | [L47](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L47) | [L24](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S4RemoteStateTest.kt#L24) | Identical. A real click on both sides. |
| 5. Read the state back | [L51](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S4RemoteStateTest.kt#L51) | [L27](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S4RemoteStateTest.kt#L27) | Both wait for the state to change instead of sleeping. The difference is that one side is typed and refactorable and the other is a code string. |

## S5. A hand-written page object

Starter: [pages/ProjectPanelUi.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt) and [S5PageObjectTest.kt](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S5PageObjectTest.kt)  
Remote Robot: [pages/ProjectPanelFixture.kt](remote-robot/src/test/kotlin/com/example/migration/remoterobot/pages/ProjectPanelFixture.kt) and [S5PageObjectTest.kt](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S5PageObjectTest.kt)

| Step | Starter and Driver | Remote Robot | What changed                                                                                                                                                                                                                                                                                           |
|---|---|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. The wrapper class | [L8](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L8) | [L19](remote-robot/src/test/kotlin/com/example/migration/remoterobot/pages/ProjectPanelFixture.kt#L19) | The Remote Robot base class is a toolbox. `CommonContainerFixture` already has about thirty finders in it: `jTree()`, `button()`, `jList()` and so on. The Starter base class has none of these. `UiComponent` has only `x()` and `xx()`, and anything else comes from an imported extension function. |
| 2. A single child | [L11](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L11) | [L28](remote-robot/src/test/kotlin/com/example/migration/remoterobot/pages/ProjectPanelFixture.kt#L28) | Driver's `x { }` is lazy: nothing is searched until the field is used, so a `val` works. The robot's `jTree()` searches at once, so the child has to be a `get()`, or the object could not exist before the panel is on screen. Either way the lookup waits when the child is used.                    |
| 3. A collection of children | [L15](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L15) | [L32](remote-robot/src/test/kotlin/com/example/migration/remoterobot/pages/ProjectPanelFixture.kt#L32) | Identical. Neither collection waits, so the caller decides how long to wait. |
| 4. How the wrapper is found | [L22](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/pages/ProjectPanelUi.kt#L22) | [L37](remote-robot/src/test/kotlin/com/example/migration/remoterobot/pages/ProjectPanelFixture.kt#L37) | A function returning `x(MyUi::class.java) { }` (starter) with a typed query, versus a `@DefaultXpath` annotation (Remote Robot) holding a raw xpath string that nothing checks until the test runs.                                                                                                                             |
| 5. Use the wrapper | [L22](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S5PageObjectTest.kt#L22) | [L27](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S5PageObjectTest.kt#L27) | Same shape on both sides.                                                                                                                                                                                                                                                                              |
| 6. Assert through it | [L25](starter-driver/src/integrationTest/kotlin/com/example/migration/starter/S5PageObjectTest.kt#L25) | [L30](remote-robot/src/test/kotlin/com/example/migration/remoterobot/S5PageObjectTest.kt#L30) | Driver has `shouldBe()` and `hasSubtext()`. Remote Robot collects the rows and searches them by hand inside a `waitForIgnoringError()`.                                                                                                                                                                      |
