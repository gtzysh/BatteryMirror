// 【应用模块】给努比亚 Z70 用的 Compose 界面，读取系统电池接口和电量计节点。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.batterymirror.z70"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.batterymirror.z70"
        minSdk = 29
        targetSdk = 35
        // 【版本】1.2.10：四种尺寸都按各自图例上色；2×2 固定八项，不再收成 2×1 或 1×2。
        versionCode = 13
        versionName = "1.2.10"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 【发布】不依赖混淆也能直接装到手机上，避免反射读取机型名被裁掉。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
}
