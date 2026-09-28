pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.grim.ac/snapshots") { content { includeGroup("ac.grim.grimac") } }
        // Optional local cache used by the supplied verification environment.
        if (file("../../work/m2").isDirectory) {
            maven { url = uri("../../work/m2"); content { includeGroup("io.papermc.paper") } }
        }
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "DisableDebuffAndTipped"
