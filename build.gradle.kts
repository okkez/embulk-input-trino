plugins {
    java
    signing
    id("org.embulk.embulk-plugins") version("0.6.2")
    id("com.diffplug.spotless") version("6.25.0")
    id("cl.franciscosolis.sonatype-central-upload") version("1.0.3")
}

group = "io.github.okkez"
version = "0.0.1"
description = "Trino input plugin for Embulk loads records from Trino using trino-client."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withJavadocJar()
    withSourcesJar()
}

spotless {
    java {
        importOrder()
        removeUnusedImports()
        googleJavaFormat()
    }
}

tasks.compileJava {
    dependsOn(tasks.spotlessApply)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.embulk:embulk-api:0.10.43")
    compileOnly("org.embulk:embulk-spi:0.11")
    implementation("org.embulk:embulk-api:0.10.43") {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "org.msgpack", module = "msgpack-core")
    }
    implementation("org.embulk:embulk-spi:0.11") {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "org.msgpack", module = "msgpack-core")
    }
    implementation("org.embulk:embulk-util-config:0.4.1") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.datatype", module = "jackson-datatype-jdk8")
        exclude(group = "javax.validation", module = "validation-api")
    }
    implementation("io.trino:trino-client:440") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.datatype", module = "jackson-datatype-jdk8")
    }

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.16.1")

//    testImplementation platform("org.junit:junit-bom:5.9.1")
//    testImplementation "org.junit.jupiter:junit-jupiter"
//    testImplementation "org.embulk:embulk-api:0.10.43"
//    testImplementation "org.embulk:embulk-spi:0.11"
}

//test {
//    useJUnitPlatform()
//}

embulkPlugin {
    mainClass = "io.github.okkez.embulk.input.trino.TrinoInputPlugin"
    category = "input"
    type = "trino"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])

            pom {
                packaging = "jar"
                name = project.name
                description = project.description
                url = "https://github.com/okkez/embulk-input-trino"

                licenses {
                    license {
                        // http://central.sonatype.org/pages/requirements.html#license-information
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/okkez/embulk-input-trino.git"
                    developerConnection = "scm:git:git@github.com:okkez/embulk-input-trino.git"
                    url = "https://github.com/okkez/embulk-input-trino"
                }
                developers {
                    developer {
                        name = "okkez"
                        email = "okkez000@gmail.com"
                    }
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}

tasks.withType<Sign>().configureEach() {
    onlyIf { System.getenv()["SKIP_SIGNING"] == null }
}

tasks.sonatypeCentralUpload {
    dependsOn("jar", "sourcesJar", "javadocJar", "generatePomFileForMavenPublication")
    username = System.getenv("SONATYPE_CENTRAL_USERNAME")
    password = System.getenv("SONATYPE_CENTRAL_PASSWORD")

    archives = files(
        tasks.named("jar"),
        tasks.named("sourcesJar"),
        tasks.named("javadocJar"),
    )
    pom = file(tasks.named("generatePomFileForMavenPublication").get().outputs.files.single())

    signingKey = System.getenv("PGP_SIGNING_KEY")
    signingKeyPassphrase = System.getenv("PGP_SIGNING_KEY_PASSPHRASE")

    publishingType = System.getenv().getOrDefault("SONATYPE_CENTRAL_PUBLISHING_TYPE", "MANUAL")
}

tasks.gem {
    from("LICENSE.txt")
    setProperty("authors", listOf("okkez"))
    setProperty("email", listOf("okkez000@gmail.com"))
    setProperty("description", "Trino input plugin for Embulk")
    setProperty("summary", "Trino input plugin for Embulk")
    setProperty("homepage", "https://github.com/okkez/embulk-input-trino")
    setProperty("licenses", listOf("Apache-2.0"))
}

// For local testing
tasks.register("cacheToMavenLocal", Sync::class) {
    from(File(gradle.gradleUserHomeDir, "caches/modules-2/files-2.1"))
    into(repositories.mavenLocal().url)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    eachFile {
        val parts: List<String> = path.split("/")
        path = listOf(parts[0].replace(".", "/"), parts[1], parts[2], parts[4]).joinToString("/")
    }
    includeEmptyDirs = false
}

