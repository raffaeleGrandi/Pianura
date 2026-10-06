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

application {
    mainClass.set("testRobots4.MainClassTest04")
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
