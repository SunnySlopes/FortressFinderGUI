cmake_minimum_required(VERSION 3.15)

project(fortressFinderLibJ LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

find_package(JNI REQUIRED)
find_package(Java REQUIRED COMPONENTS Development)

set(PROJECT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/..)
set(JAVA_BRIDGE_SRC ${PROJECT_ROOT}/src/main/java/sunnyslopes/fortressfinder/FortressFinderBridge.java)
set(JNI_GEN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/generated)
set(JNI_GEN_HEADER ${JNI_GEN_DIR}/sunnyslopes_fortressfinder_FortressFinderBridge.h)

add_custom_command(
        OUTPUT ${JNI_GEN_HEADER}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${JNI_GEN_DIR}
        COMMAND ${Java_JAVAC_EXECUTABLE} -encoding UTF-8 -h ${JNI_GEN_DIR} ${JAVA_BRIDGE_SRC}
        DEPENDS ${JAVA_BRIDGE_SRC}
        COMMENT "Generating JNI header from FortressFinderBridge.java"
        VERBATIM
)

add_custom_target(generate_jni_header DEPENDS ${JNI_GEN_HEADER})

set(SOURCES
        sunnyslopes_fortressfinder_FortressFinderBridge.cpp
)

add_library(fortressFinderLibJ SHARED ${SOURCES} ${JNI_GEN_HEADER})

add_dependencies(fortressFinderLibJ generate_jni_header)

target_link_libraries(fortressFinderLibJ PRIVATE cubiomes_static)

target_include_directories(fortressFinderLibJ PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/..
        ${cubiomes_SOURCE_DIR}
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

# Skip strip on Windows: a locked DLL (app still running) would fail the whole link step.
if(NOT MSVC AND NOT WIN32)
    add_custom_command(TARGET fortressFinderLibJ POST_BUILD
            COMMAND ${CMAKE_STRIP} $<TARGET_FILE:fortressFinderLibJ>
    )
endif()
