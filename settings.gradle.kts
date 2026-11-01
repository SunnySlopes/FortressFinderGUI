rootProject.name = "FortressFinder"

// Native search core. FortressFinderBase owns the C++ engine, the JNI library
// and its nested cubiomes submodule; the main module only consumes its
// artifacts. See the contract comment in build.gradle.
include("fortressfinderbase")
project(":fortressfinderbase").projectDir = file("FortressFinderBase")
