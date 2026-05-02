// Root build for the DTMF-Decoder v2 multi-module project.
//
// Per Task 1.6 of the dtmf-v2-foundation spec: the root applies no plugins
// itself. Its sole responsibility is to stamp consistent Maven coordinates
// — group `com.tino1b2be` and version `2.0.0` — onto every subproject
// (Requirements 2.1, 2.2, 2.3).
//
// `allprojects` (rather than `subprojects`) is used deliberately. The root
// itself does not publish artifacts, so applying the group/version to it is
// harmless, and `allprojects` keeps the intent — "every project in this
// build carries these coordinates" — visible in one place. Subproject-level
// build scripts (added in Task 1.7) inherit these values without having to
// repeat them.

allprojects {
    group = "com.tino1b2be"
    version = "2.0.0"
}

// -------------------------------------------------------------------------
// Aggregate Javadoc for GitHub Pages
// -------------------------------------------------------------------------
//
// The GitHub Pages workflow (.github/workflows/pages.yml) runs
// `./gradlew aggregateJavadoc` on every push to master and deploys the
// output at `build/javadoc-site/` as the project's Pages site. The task
// merges every published module's main-source javadoc under a single
// directory:
//
//   build/javadoc-site/
//     index.html                  ← landing page listing the modules
//     goertzel/                   ← :goertzel javadoc
//     dtmf-core/                  ← :dtmf-core javadoc
//
// Consumers can browse the full API at tino1b2be.github.io/DTMF-Decoder.

val publishedModuleNames = setOf("goertzel", "dtmf-core")

tasks.register("aggregateJavadoc") {
    group = "documentation"
    description = "Aggregate Javadoc from every published module into build/javadoc-site/."

    val siteDir = layout.buildDirectory.dir("javadoc-site")
    val publishedModules = subprojects.filter { it.name in publishedModuleNames }

    dependsOn(publishedModules.map { "${it.path}:javadoc" })

    val inputDirs = publishedModules.associate { module ->
        module.name to module.layout.buildDirectory.dir("docs/javadoc")
    }
    inputDirs.values.forEach { inputs.dir(it) }
    outputs.dir(siteDir)

    doLast {
        val site = siteDir.get().asFile
        site.deleteRecursively()
        site.mkdirs()

        inputDirs.forEach { (name, dirProvider) ->
            val source = dirProvider.get().asFile
            val target = site.resolve(name)
            source.copyRecursively(target, overwrite = true)
        }

        val landing = site.resolve("index.html")
        landing.writeText(buildLandingHtml(publishedModules.map { it.name }))
    }
}

fun buildLandingHtml(moduleNames: List<String>): String {
    val listItems = moduleNames.joinToString("\n") { name ->
        """    <li><a href="$name/index.html"><code>$name</code></a></li>"""
    }
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>DTMF-Decoder API documentation</title>
<style>
  body { font-family: system-ui, -apple-system, sans-serif; max-width: 720px; margin: 3rem auto; padding: 0 1rem; color: #222; }
  h1 { margin-bottom: 0.25rem; }
  p.tagline { color: #666; margin-top: 0; }
  ul { padding-left: 1.5rem; line-height: 1.9; }
  code { background: #f3f3f3; padding: 0.1rem 0.35rem; border-radius: 3px; }
  a { color: #0a60c6; text-decoration: none; }
  a:hover { text-decoration: underline; }
  footer { margin-top: 3rem; padding-top: 1rem; border-top: 1px solid #eee; color: #888; font-size: 0.9rem; }
</style>
</head>
<body>
<h1>DTMF-Decoder</h1>
<p class="tagline">A Java 17 library for detecting and generating DTMF signalling tones per ITU-T Q.23 and Q.24.</p>

<h2>API documentation</h2>
<ul>
$listItems
</ul>

<h2>Project links</h2>
<ul>
  <li><a href="https://github.com/tino1b2be/DTMF-Decoder">Source on GitHub</a></li>
  <li><a href="https://github.com/tino1b2be/DTMF-Decoder/blob/master/docs/requirements.md">Requirements</a></li>
  <li><a href="https://github.com/tino1b2be/DTMF-Decoder/blob/master/docs/design.md">Design</a></li>
</ul>

<footer>
  Generated from the <code>master</code> branch on every push. Currently documenting version ${project.version}.
</footer>
</body>
</html>
"""
}
