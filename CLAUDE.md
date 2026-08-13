# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Joget DX plugin (OSGi bundle) that guards against accidentally uninstalling a plugin that a published
app still depends on, and lets admins proactively look up plugin usage from the Plugin Manager page. It
injects JS into two Plugin Manager pages and exposes a JSON web service that checks which published apps
use a given plugin's jar.

## Build

```bash
mvn clean install
```

Produces an OSGi bundle jar (via `maven-bundle-plugin`) at `target/uninstall-plugin-alert-9.1.0.jar`, to be
dropped into a Joget DX `wflow/app_plugins` directory.

Requires the `wflow-core` artifact (version `9.1-SNAPSHOT`, scope `provided`) to be resolvable — this is the
Joget core dependency, normally available via the internal/snapshots repos in `<distributionManagement>` or
a locally built/installed copy. Java target is 1.8.

There are no unit tests (surefire is configured with `skipTests=true`); there's no lint step.

## Architecture

- **`Activator`** — OSGi `BundleActivator`. Registers two services: `UninstallPluginAlert` and
  `ShowPluginUsage`. Any new plugin class added to this bundle must be registered here too.

- **`UninstallPluginAlert`** — extends `UiHtmlInjectorPluginAbstract` and implements `PluginWebSupport`, so it
  does two distinct jobs from one class:
  1. **HTML injection** (`getHtml`, `getInjectUrlPatterns`): scoped only to `/web/console/setting/plugin`
     (the Plugin Manager page). Renders `templates/UninstallPluginAlertUiHtmlInjector.ftl`, which overrides
     the page's global `window.uninstall(selectedList)` JS function to intercept the uninstall action.
  2. **Web service** (`webService`), reachable at
     `/web/json/plugin/org.joget.marketplace.UninstallPluginAlert/service`: takes a POSTed
     `{selectedList: [...]}` of plugin class names, and returns `{ids, names, jars}` describing which
     published apps depend on those plugins' jars.

- **Dependency-detection flow** (the core logic, in `getPublishedApps`):
  1. Resolve each selected plugin class to its jar filename via `PluginManager.getJarFileName()`.
  2. Query `app_app` directly (raw JDBC against the `setupDataSource` bean) for all `published = 1` apps.
  3. For each published app, load its bundled plugin jars via `AppDevUtil.getPluginJarList(appDef)` (the
     same mechanism used by app export).
  4. Compare jar names after **normalizing** them (`normalizeJarName`: strip `.jar`, Windows `(n)` copy
     suffixes, and version/SNAPSHOT/qualifier suffixes) — so a match isn't missed just because the jar in
     the app differs in version from the jar being uninstalled.
  5. Any app with a matching jar is added to the result set (`appId` → `appName`).

  There is a large commented-out alternate approach (`executeQery` + `getSql`) that instead searched for the
  plugin class name via `LIKE '%class%'` across JSON/property columns in `app_builder`, `app_form`,
  `app_datalist`, `app_userview`, `app_plugin_default`, `app_resource`, `app_package_activity_plugin`, and
  `app_package_participant`. This was abandoned in favor of the jar-comparison approach above — keep that in
  mind before reviving it (it doesn't handle the version-suffix problem `normalizeJarName` solves, but it
  does inspect actual usage sites rather than just "jar is bundled with the app").

- **`templates/UninstallPluginAlertUiHtmlInjector.ftl`** — the injected `<script>`. Shows a `UI.showConsoleToast`
  loading toast while the web service call is in flight (checking usage across all published apps can take a
  few seconds), then builds an HTML bullet list of dependent app names and shows a `UI.confirm` dialog (the
  same SweetAlert2-based modal used for "Are you sure to unpublish this App?") before continuing with the
  real uninstall POST to `/web/console/setting/plugin/uninstall`. `UI.confirm`/`UI.showConsoleToast` require
  Joget 9.1+ (SweetAlert2 was introduced in 9.1) — this is why the plugin's baseline version is 9.1.0, not 8.2.

- **`ShowPluginUsage`** — a second `UiHtmlInjectorPluginAbstract`, scoped to
  `/web/console/setting/plugin/details`. That's a *different* URL from the main plugin list
  (`/web/console/setting/plugin`): it's the page Joget loads inside an iframe popup when you click a bundle
  row in the "Installed Plugins" table, showing the individual plugin classes registered inside that OSGi
  bundle (`PluginManager.listBundlePlugins`). Because Joget's `AppUtil.getInjectionHtml()` matches injection
  URL patterns with exact/Ant-style matching (not prefix matching), this popup needs its own injector — it
  won't pick up patterns registered against the main list page.
  - `templates/ShowPluginUsageUiHtmlInjector.ftl` binds a click handler on that popup's table rows. Joget's
    own `ui.js` gives each row an `id` of `"row" + <fully-qualified class name, dots replaced with "__dot__">`
    (this is also how the existing `uninstall()` checkbox flow recovers class names) — reading `this.id` is
    enough to know which plugin class was clicked, no extra server round-trip needed to resolve it.
  - Rather than duplicating the usage-detection logic, it POSTs `{selectedList: [<one class>]}` straight to
    `UninstallPluginAlert`'s existing web service and renders the result via `Swal.fire` (a plain info dialog,
    not `UI.confirm` — there's no "confirm/cancel" semantics here, it's just a lookup).
  - This popup is rendered via `commons:popupHeader`/`popupFooter` tags, a lighter layout than the main
    console shell, so the FTL feature-detects `UI`/`Swal` before using them and falls back to `alert()` if
    they aren't loaded on that page.

## Conventions specific to this plugin template

- The `Import-Package` / `Export-Package` / `Bundle-Activator` OSGi instructions in `pom.xml` are meant to be
  edited per-plugin ("Change package and plugin class here" comment) — if cloning this as a template for a
  different plugin, update `Bundle-Activator` and trim `Import-Package` to only what's actually used.
- `getVersion()` in `UninstallPluginAlert.java` and the Maven `<version>` should be kept in sync (both
  currently `9.1.0`). Convention for this plugin family is to set the version to the *lowest* Joget major.minor
  the plugin actually requires — 9.1.0 here because `UI.confirm`/`UI.showConsoleToast` (SweetAlert2) need 9.1+.
- README.md / CODE_OF_CONDUCT.md are unmodified JogetOSS `repo-template` boilerplate, not specific to this
  plugin's behavior.
