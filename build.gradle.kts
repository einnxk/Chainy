plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
}

group = "dev.einnik.chainy"
version = "1.0.0-SNAPSHOT"

val targetVersion = 25
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetVersion))
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(rootProject.libs.lombok)
    annotationProcessor(rootProject.libs.lombok)

    api(rootProject.libs.jspecify)

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

spotless {
    java {
        licenseHeaderFile(rootProject.file("config/license-header.txt"), "^(package|import|module) ")
        googleJavaFormat()
    }
}