/*
 *     AsyncMC - A fully async, non blocking, thread safe and open source Minecraft server implementation
 *     Copyright (C) 2020 joserobjr@gamemods.com.br
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published
 *     by the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.15.1")
    }
}

val kotlinVersion = "1.4.21"
plugins {
    kotlin("jvm") version "1.4.21"
    jacoco
    `maven-publish`
    
}

apply {
    plugin("kotlinx-atomicfu")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

val moduleName = "com.github.asyncmc.protocol.raknet"
val isSnapshot = version.toString().endsWith("SNAPSHOT")

repositories {
    jcenter()
    maven(url = "https://repo.gamemods.com.br/public/")
}

tasks.withType<JavaCompile>().configureEach {
    options.javaModuleVersion.set(provider { project.version as String })

    // this is needed because we have a separate compile step in this example with the 'module-info.java' is in 'main/java' and the Kotlin code is in 'main/kotlin'
    //options.compilerArgs = listOf("--patch-module", "$moduleName=${sourceSets.main.get().output.asPath}")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "15"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":compileKotlin")
    inputs.property("moduleName", moduleName)
    doFirst {
        options.compilerArgs = options.compilerArgs + listOf(
            // include Gradle dependencies as modules
            "--module-path", sourceSets["main"].compileClasspath.asPath,
            "--patch-module", "$moduleName=${sourceSets.main.get().output.asPath}"
        )
        sourceSets["main"].compileClasspath = files()
    }
}

sourceSets.main.configure {
    //java.setSrcDirs(listOf("src/main/kotlin"))
}

plugins.withType<JavaPlugin>().configureEach {
    configure<JavaPluginExtension> {
        modularity.inferModulePath.set(true)
    }
}

val ktorVersion = findProperty("ktor.version")
val log4j2Version = findProperty("log4j2.version")
val slf4jVersion = findProperty("slf4j.version")

dependencies {
    api(kotlin("stdlib-jdk8", kotlinVersion))
    api(kotlin("reflect", kotlinVersion))
    api("com.github.asyncmc:raknet-api:0.1.0-SNAPSHOT")

    implementation("org.jctools:jctools-core:3.0.0")
    implementation("io.ktor:ktor-network:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.2")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")

    testImplementation(kotlin("test-junit5", kotlinVersion))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0-M1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0-M1")

    testImplementation("org.mockito:mockito-junit-jupiter:3.3.3")
    testImplementation("org.mockito:mockito-inline:3.3.3")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")

    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("com.natpryce:hamkrest:1.7.0.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    testLogging {
        events("PASSED", "FAILED", "SKIPPED", "STANDARD_OUT", "STANDARD_ERROR")
    }
}

jacoco {
    //toolVersion = jacocoVersion
    reportsDir = file("$buildDir/reports/jacoco")
}

tasks {
    named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        classDirectories.setFrom(files("${buildDir}/classes"))
        reports {
            xml.isEnabled = true
            html.isEnabled = true
        }
    }

    create<Jar>("sourceJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    withType<Jar>().configureEach {
        from(projectDir) {
            include("LICENSE.txt")
            include("NOTICE.md")
        }
    }
}


fun findProp(name: String) = findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
    ?: System.getenv(name.replace('.', '_').toUpperCase())?.takeIf { it.isNotBlank() }

publishing {
    repositories {
        maven {
            val prefix = if (isSnapshot) "asyncmc.repo.snapshot" else "asyncmc.repo.release"
            url = uri(findProp("$prefix.url") ?: "$buildDir/repo")
            when(findProp("$prefix.auth.type")) {
                "password" -> credentials {
                    username = findProp("$prefix.auth.username")
                    password = findProp("$prefix.auth.password")
                }
                "aws" -> credentials(AwsCredentials::class.java) {
                    accessKey = findProp("$prefix.auth.access_key")
                    secretKey = findProp("$prefix.auth.secret_key")
                    sessionToken = findProp("$prefix.auth.session_token")
                }
                "header" -> credentials(HttpHeaderCredentials::class.java) {
                    name = findProp("$prefix.auth.header_name")
                    value = findProp("$prefix.auth.header_value")
                }
            }
        }
    }

    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifact(tasks["sourceJar"])
            pom {
                name.set("RakNet Protocol")
                description.set("An async, non blocking, open source, copyleft RakNet library written in Kotlin")
                url.set("https://github.com/AsyncMC/RakNet-Protocol")
                licenses {
                    license {
                        name.set("GNU Affero General Public License, Version 3")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                    }
                }
                developers {
                    developer {
                        id.set("joserobjr")
                        name.set("José Roberto de Araújo Júnior")
                        email.set("joserobjr@gamemods.com.br")
                    }
                }
                scm {
                    url.set("https://github.com/AsyncMC/RakNet-Protocol")
                    connection.set("scm:git:https://github.com/AsyncMC/RakNet-Protocol.git")
                    developerConnection.set("https://github.com/AsyncMC/RakNet-Protocol.git")
                }
            }
        }
    }
}
