import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ktlintCli = configurations.create("ktlintCli")
val detektCli = configurations.create("detektCli")
val automatedVersionCode = providers.environmentVariable("RELEASE_VERSION_CODE").orNull?.toIntOrNull()
val automatedVersionName = providers.environmentVariable("RELEASE_VERSION_NAME").orNull?.removePrefix("v")
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
val googleWebClientId =
    providers.environmentVariable("GOOGLE_WEB_CLIENT_ID").orNull
        ?: localProperties.getProperty("GOOGLE_WEB_CLIENT_ID").orEmpty()

android {
    namespace = "hr.bebindnevnik.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "hr.bebindnevnik.app"
        minSdk = 29
        targetSdk = 37
        versionCode = automatedVersionCode ?: 1_006_001
        versionName = automatedVersionName ?: "1.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.replace("\"", "\\\"")}\"")
    }

    val keystoreFile = rootProject.file("keystore.properties")
    val keystoreProperties =
        Properties().apply {
            if (keystoreFile.exists()) keystoreFile.inputStream().use(::load)
        }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Bebin dnevnik – Debug"
        }
        release {
            manifestPlaceholders["appLabel"] = "Bebin dnevnik"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    testOptions {
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        htmlReport = true
        sarifReport = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.sqlite:sqlite:2.7.0")
    implementation("net.zetetic:sqlcipher-android:4.17.0@aar")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    ktlintCli("com.pinterest.ktlint:ktlint-cli:1.8.0")
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:1.23.8")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.json:json:20260719")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.work:work-testing:2.11.2")
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Pokreće statičku analizu svih Kotlin izvora."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        "src/main/java,src/test/java,src/androidTest/java",
        "--config",
        "$rootDir/config/detekt/detekt.yml",
        "--build-upon-default-config",
        "--parallel",
    )
}

tasks.register<JavaExec>("formatCheck") {
    group = "verification"
    description = "Provjerava format svih Kotlin izvora putem ktlinta."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args("src/**/*.kt", "build.gradle.kts", "!**/build/**")
}

tasks.register<JavaExec>("formatApply") {
    group = "formatting"
    description = "Formatira sve Kotlin izvore putem ktlinta."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", "src/**/*.kt", "build.gradle.kts", "!**/build/**")
}

tasks.register("verifySafetyInvariants") {
    group = "verification"
    description = "Provjerava produkcijski identitet i zabranu destruktivnih Room migracija."
    doLast {
        check(android.defaultConfig.applicationId == "hr.bebindnevnik.app")
        check(android.buildTypes.getByName("debug").applicationIdSuffix == ".debug")
        val sources = fileTree("src/main/java") { include("**/*.kt") }.files.joinToString("\n") { it.readText() }
        check("fallbackToDestructiveMigration" !in sources) { "Destruktivne Room migracije su zabranjene." }
        check("deleteDatabase(" !in sources) { "Produkcijski kod ne smije automatski brisati bazu." }
    }
}
