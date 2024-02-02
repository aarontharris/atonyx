# Status

Semi-Functional -- Work in Progress

# Goal

Simplified abstraction layer around Onyx SDK for Android to help encourage more development for my
beloved Onyx Boox Note Air 3

# Notes

[Notes](NOTES.md)

# Project Setup

```
cd <yourproject>
git clone git@github.com:aarontharris/atonyx.git
git submodule update --init
```

### settings.gradle.kts

Required Repos

```
dependencyResolutionManagement {
    repositories {
        maven { setUrl("https://repo.boox.com/repository/maven-public/") }
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}
```

Include atonyx

```
include(":atonyx")
```

### app/build.gradle.kts

```
android {
    defaultConfig {
        ndk { abiFilters.add("armeabi-v7a") }
    }
    packaging {
        jniLibs {
            pickFirsts.add("lib/*/libc++_shared.so")
        }
    }
}
```

### gradle.properties

I had to add the following due to some api conflict.

```
android.enableJetifier=true
```

## Application & AndroidManifest

A custom Application class must be added to AndroidManifest.
Also, the following must be executed within the Custom Application.

```
override fun onCreate() {
    super.onCreate()
    AtOnyxApp.onCreate(this)
}
```

# Getting Started

```
// Psuedo Code
class MyActivity : AppCompatActivity {
    private val onx = AtOnyx()
    private lateinit var surfaceview : SurfaceView // from your layout

    fun onCreate( savedInstanceState : Bundle ) {
        super.onCreate(savedInstanceState)
        onx.doCreate(surfaceview)
    }
    
    fun onResume() {
        super.onResume()
        onx.doResume()
    }
    
    fun onPause() {
        super.onPause()
        onx.doPause()
    }
    
    fun onDestroy() {
        super.onDestroy()
        onx.doDestroy()
    }
}
```