plugins { java }
group = "me.jade"
version = "1.1.0"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation("org.mockito:mockito-core:5.18.0")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
tasks.processResources { filesMatching("plugin.yml") { expand("version" to project.version) } }
tasks.test { useJUnitPlatform() }
