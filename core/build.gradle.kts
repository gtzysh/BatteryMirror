// 【核心模块】不依赖 Android，容量和用电量算法可以在普通 JDK 上单测。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // 【工具链】这台环境只有 JDK 21，字节码仍由 21 编出来即可。
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
}
