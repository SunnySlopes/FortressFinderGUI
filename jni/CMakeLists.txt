cmake_minimum_required(VERSION 3.15)

project(fortressFinderLibJ LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# FindJNI breaks when JAVA_HOME contains spaces on Windows (e.g. C:\Program Files\Java\...).
# Resolve JAVA_HOME manually and pass -DJAVA_HOME=... from Gradle with forward slashes.
if(DEFINED JAVA_HOME)
    set(_JAVA_HOME "${JAVA_HOME}")
elseif(DEFINED ENV{JAVA_HOME})
    set(_JAVA_HOME "$ENV{JAVA_HOME}")
else()
    find_program(_JAVA_EXE java REQUIRED)
    get_filename_component(_JAVA_BIN "${_JAVA_EXE}" DIRECTORY)
    get_filename_component(_JAVA_HOME "${_JAVA_BIN}" DIRECTORY)
endif()

file(TO_CMAKE_PATH "${_JAVA_HOME}" _JAVA_HOME_CMAKE)

set(JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include")
if(WIN32)
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/win32")
elseif(APPLE)
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/darwin")
else()
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/linux")
endif()

find_program(Java_JAVAC_EXECUTABLE javac
        HINTS "${_JAVA_HOME_CMAKE}/bin"
        REQUIRED
)

set(PROJECT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/..)
set(JAVA_BRIDGE_SRC ${PROJECT_ROOT}/src/main/java/sunnyslopes/fortressfinder/FortressFinderBridge.java)
set(JNI_GEN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/generated)
set(JNI_GEN_HEADER ${JNI_GEN_DIR}/sunnyslopes_fortressfinder_FortressFinderBridge.h)

set(CUBIOMES_DIR ${PROJECT_ROOT}/cubiomes)
if(NOT EXISTS "${CUBIOMES_DIR}/finders.c")
    message(FATAL_ERROR
            "cubiomes not found at ${CUBIOMES_DIR}. "
            "Put a local copy in cubiomes/ for packaging, or fetch the submodule: git submodule update --init --recursive")
endif()

add_custom_command(
        OUTPUT ${JNI_GEN_HEADER}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${JNI_GEN_DIR}
        COMMAND ${Java_JAVAC_EXECUTABLE} -encoding UTF-8 -h ${JNI_GEN_DIR} ${JAVA_BRIDGE_SRC}
        DEPENDS ${JAVA_BRIDGE_SRC}
        COMMENT "Generating JNI header from FortressFinderBridge.java"
        VERBATIM
)

add_custom_target(generate_jni_header DEPENDS ${JNI_GEN_HEADER})

set(CUBIOMES_SOURCES)
file(GLOB _cubiomes_root_c "${CUBIOMES_DIR}/*.c")
foreach(_src ${_cubiomes_root_c})
    get_filename_component(_name "${_src}" NAME)
    if(NOT _name STREQUAL "tests.c")
        list(APPEND CUBIOMES_SOURCES "${_src}")
    endif()
endforeach()
file(GLOB _cubiomes_feat_c "${CUBIOMES_DIR}/features/*.c")
list(APPEND CUBIOMES_SOURCES ${_cubiomes_feat_c})

add_library(fortressFinderLibJ SHARED
        sunnyslopes_fortressfinder_FortressFinderBridge.cpp
        ${CUBIOMES_SOURCES}
        ${JNI_GEN_HEADER}
)

add_dependencies(fortressFinderLibJ generate_jni_header)

target_include_directories(fortressFinderLibJ PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/..
        ${CUBIOMES_DIR}
        ${CUBIOMES_DIR}/features
)

if(MSVC)
    target_compile_options(fortressFinderLibJ PRIVATE /O2 /DNDEBUG)
else()
    target_compile_options(fortressFinderLibJ PRIVATE -O3 -DNDEBUG -ffast-math)
endif()

if(MINGW)
    target_link_options(fortressFinderLibJ PRIVATE
            -static
            -static-libgcc
            -static-libstdc++
    )
endif()

set_target_properties(fortressFinderLibJ PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/native
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/native
)

if(NOT MSVC AND NOT WIN32)
    find_program(CMAKE_STRIP strip)
    if(CMAKE_STRIP)
        add_custom_command(TARGET fortressFinderLibJ POST_BUILD
                COMMAND ${CMAKE_STRIP} $<TARGET_FILE:fortressFinderLibJ>
        )
    endif()
endif()
