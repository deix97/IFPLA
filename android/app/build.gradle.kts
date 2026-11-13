import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty

plugins {
    id("com.android.application")
}

/* Bei jedem Lauf in GitHub Actions eine höhere Nummer, damit Android das Update annimmt */
val bau = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "io.github.deix97.fahrplan"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.deix97.fahrplan"
        minSdk = 26
        targetSdk = 34
        versionCode = bau
        versionName = "1.0.$bau"
    }

    signingConfigs {
        create("fahrplan") {
            storeFile = rootProject.file("fahrplan.keystore")
            storePassword = "fahrplan"
            keyAlias = "fahrplan"
            keyPassword = "fahrplan"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fahrplan")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fahrplan")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/* Die Anzeige selbst (index.html, pdf.js, Symbole) liegt im Projektstamm; sie wird
   bei jedem Bau in die App gelegt, damit App und Webseite dieselbe Fassung zeigen. */
abstract class WebDateien : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dateien: ConfigurableFileCollection

    @get:Internal
    abstract val basis: DirectoryProperty

    @get:OutputDirectory
    abstract val ausgabe: DirectoryProperty

    @TaskAction
    fun kopieren() {
        val ziel = ausgabe.get().asFile.resolve("web")
        ziel.deleteRecursively()
        val b = basis.get().asFile
        dateien.files.forEach { f ->
            val t = ziel.resolve(f.relativeTo(b).path)
            t.parentFile.mkdirs()
            f.copyTo(t, overwrite = true)
        }
    }
}

val webDateien = tasks.register<WebDateien>("webDateien") {
    val stamm = rootProject.layout.projectDirectory.dir("..")
    basis.set(stamm)
    dateien.from(rootProject.fileTree("..") {
        include("index.html", "lib/pdf.min.js", "lib/pdf.worker.min.js", "lib/LICENSE", "symbol/*.png")
    })
    ausgabe.set(layout.buildDirectory.dir("generated/web"))
}

androidComponents {
    onVariants { variante ->
        variante.sources.assets?.addGeneratedSourceDirectory(webDateien, WebDateien::ausgabe)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
