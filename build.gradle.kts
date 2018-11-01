import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.3.0"
    kotlin("jvm").version(kotlinVersion)
    id("org.jetbrains.kotlin.plugin.spring").version(kotlinVersion)
    id("org.jetbrains.kotlin.plugin.allopen").version(kotlinVersion)
    id("com.github.ben-manes.versions").version("0.20.0")
}

repositories {
    jcenter()
    maven("https://oss.jfrog.org/libs-snapshot")
}

val kotlinVersion = "1.3.0"
val springBootVersion = "2.1.0.RELEASE"
val rsocketVersion = "0.11.11"
val kotlinLoggingVersion = "1.6.10"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("io.rsocket.kotlin:rsocket-core:0.9-SNAPSHOT")
    implementation("io.rsocket.kotlin:rsocket-transport-netty:0.9-SNAPSHOT")
//    implementation("io.rsocket.kotlin:rsocket-transport-okhttp:0.9-SNAPSHOT")

//    implementation("io.rsocket:rsocket-core:$rsocketVersion")
//    implementation("io.rsocket:rsocket-transport-netty:$rsocketVersion")
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.springframework.boot:spring-boot-starter-logging")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.getByName<Wrapper>("wrapper") {
    gradleVersion = "5.0-rc-1"
    distributionType = Wrapper.DistributionType.ALL
}
