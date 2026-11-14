pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
        maven("https://devrepo.kakaomobility.com/repository/kakao-mobility-android-knsdk-public")
        maven("https://jitpack.io")
    }
}
rootProject.name = "MoaDrive"
include(":app")
