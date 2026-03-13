plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.mkultra69"
version = "0.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.shadowJar {
    configurations = project.configurations.runtimeClasspath.map { setOf(it) }

    dependencies {
        exclude { it.moduleGroup != "org.bstats" }
    }

    relocate("org.bstats", "${project.group}.chainmailsieve.libs.bstats")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
