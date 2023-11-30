import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.*

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.dependencyManagement)
    `maven-publish`
    alias(libs.plugins.versions)
}

group = "de.fhg.aisec.ids.clearinghouse"

val fis = FileInputStream("../clearing-house-app/logging-service/Cargo.toml")
val props = Properties()
props.load(fis)
version = props.getProperty("version").removeSurrounding("\"")

sourceSets{
    create("intTest"){
    }
}

val intTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

configurations["intTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val integrationTest = task<Test>("integrationTest") {
    // set to true for debugging
    testLogging.showStandardStreams = false
    useJUnitPlatform()

    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
    shouldRunAfter("test")
}

tasks.register("printChVersion") {

    doFirst {
        println(version)
    }
}

buildscript {
    repositories {
        mavenCentral()

        fun findProperty(s: String) = project.findProperty(s) as String?

        maven {
            name = "GitHubPackages"

            url = uri("https://maven.pkg.github.com/Fraunhofer-AISEC/ids-clearing-house-service")
            credentials(HttpHeaderCredentials::class) {
                name = findProperty("github.username")
                value = findProperty("github.token")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }   
    }
}

publishing {
    fun findProperty(s: String) = project.findProperty(s) as String?

    publications {
        create<MavenPublication>("binary") {
            artifact(tasks["jar"])
        }
    }
    repositories {
        maven {            
            name = "GitHubPackages"

            url = uri("https://maven.pkg.github.com/Fraunhofer-AISEC/ids-clearing-house-service")
            credentials(HttpHeaderCredentials::class) {
                name = findProperty("github.username")
                value = findProperty("github.token")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

repositories {
    mavenCentral()
    // References IAIS repository that contains the infomodel artifacts
    maven("https://gitlab.cc-asp.fraunhofer.de/api/v4/projects/55371/packages/maven")
}

dependencies {
    val versions = rootProject.libs.versions
    // Some versions are downgraded for unknown reasons, fix this here
    val groupPins = mapOf(
        "org.jetbrains.kotlin" to mapOf(
            "*" to versions.kotlin.get()
        ),
        "org.jetbrains.kotlinx" to mapOf(
            Regex("kotlinx-coroutines-.*") to versions.kotlinx.coroutines.get(),
            Regex("kotlinx-serialization-.*") to versions.kotlinx.serialization.get()
        ),
        "org.eclipse.jetty" to mapOf(
            "*" to versions.jetty.get()
        )
    )
    // We need to explicitly specify the kotlin version for all kotlin dependencies,
    // because otherwise something (maybe a plugin) downgrades the kotlin version,
    // which produces errors in the kotlin compiler. This is really nasty.
    configurations.all {
        resolutionStrategy.eachDependency {
            groupPins[requested.group]?.let { pins ->
                pins["*"]?.let {
                    // Pin all names when asterisk is set
                    useVersion(it)
                } ?: pins[requested.name]?.let { pin ->
                    // Pin only for specific names given in map
                    useVersion(pin)
                } ?: pins.entries.firstOrNull { e ->
                    e.key.let {
                        it is Regex && it.matches(requested.name)
                    }
                }?.let { useVersion(it.value) }
            }
        }
    }

    // Imported from IDS feature in TC at runtime
    implementation(libs.infomodel.model)
    implementation(libs.javax.validation)

    implementation(libs.camel.idscp2)
    implementation(libs.camel.core)
    implementation(libs.camel.api)
    implementation(libs.camel.jetty)

    implementation(libs.apacheHttp.core)
    implementation(libs.apacheHttp.client)
    implementation(libs.apacheHttp.mime)
    implementation(libs.commons.fileupload)
    implementation(libs.ktor.auth)
    implementation(libs.ktor.auth.jwt)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    compileOnly(libs.spring.context)

    testApi(libs.slf4j.simple)
    testImplementation(libs.idscp2.core)
    testImplementation(libs.junit5)
    testImplementation(libs.okhttp3)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.serialization.json)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

val versionRegex = ".*((rc|beta)-?[0-9]*|-(b|alpha)[0-9.]+)$".toRegex(RegexOption.IGNORE_CASE)

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        // Reject release candidates and betas and pin Apache Camel to 3.18 LTS version
        versionRegex.matches(candidate.version)
                || (candidate.group in setOf("org.apache.camel", "org.apache.camel.springboot")
                && !candidate.version.startsWith("3.18"))
    }
}
