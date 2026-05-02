// Shared Java library conventions for every subproject in dtmf-v2.
//
// Responsibilities (Task 1.5, Requirement 1.3):
//   - Apply the built-in `java-library` plugin
//   - Pin the toolchain to Java 17
//   - Enable `-Xlint:all -Werror` on every JavaCompile task
//   - Produce sources + javadoc jars (useful for published libraries and cheap
//     to opt into here so the published-library convention does not have to
//     redo it)
//   - Wire JUnit 5 + jqwik as test dependencies and configure the JUnit
//     Platform runner to load both engines
//
// Version coordinates match `gradle/libs.versions.toml`:
//   junit-jupiter = 5.10.2
//   jqwik         = 1.9.0

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Allow `javadoc` to succeed against stages where only `package-info.java`
// exists (no public/protected types yet). Real classes arrive from Stage 2
// onward; until then, `-Xdoclint:none` plus tolerating the "no public or
// protected classes found to document" case keeps `./gradlew build` green.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
    isFailOnError = false
}

repositories {
    mavenCentral()
}

dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.2")
    "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.10.2")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    "testImplementation"("net.jqwik:jqwik:1.9.0")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
}
