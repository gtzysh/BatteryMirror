// 【工程】电池镜：纯计算模块 core + 努比亚 Z70 上的 Android 界面 app。
pluginManagement {
    repositories {
        // 【镜像】国内先走阿里云，官方仓库留作后备，避免卡在 dl.google.com。
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "BatteryMirror"
include(":core")
include(":app")
