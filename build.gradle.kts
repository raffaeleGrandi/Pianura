plugins {
    java
    application
}

// Built with any JDK >= 21 (25 LTS recommended); the bytecode and the API used are Java 21,
// so JUSE runs on every JDK from 21 on.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

// ./gradlew run               -> example scenario 04, the default demo
// ./gradlew run -Pexample=07  -> any other example scenario (03..09)
val example = providers.gradleProperty("example").getOrElse("04").padStart(2, '0')

application {
    mainClass.set("io.github.raffaelegrandi.pianura.examples.scenario$example.MainClassTest$example")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")   // required since Gradle 9
}

tasks.test {
    useJUnitPlatform()
}
