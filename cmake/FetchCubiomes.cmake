# cubiomes: prefer project-root cubiomes/; otherwise FetchContent from GitHub.
# (avoid add_subdirectory: upstream CMakeLists.txt requires CMake < 3.5 policy)

get_filename_component(FORTRESS_FINDER_ROOT "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
set(CUBIOMES_LOCAL_DIR "${FORTRESS_FINDER_ROOT}/cubiomes")

function(fortress_finder_patch_cubiomes_rng _src_dir)
    set(_rng "${_src_dir}/rng.h")
    if(NOT EXISTS "${_rng}")
        message(FATAL_ERROR "cubiomes rng.h not found: ${_rng}")
    endif()
    file(READ "${_rng}" CUBIOMES_RNG_H)
    string(REPLACE "lerp2" "@@LERP2@@" CUBIOMES_RNG_H "${CUBIOMES_RNG_H}")
    string(REPLACE "lerp3" "@@LERP3@@" CUBIOMES_RNG_H "${CUBIOMES_RNG_H}")
    string(REPLACE "lerp(" "cubiomes_rng_lerp(" CUBIOMES_RNG_H "${CUBIOMES_RNG_H}")
    string(REPLACE "@@LERP2@@" "lerp2" CUBIOMES_RNG_H "${CUBIOMES_RNG_H}")
    string(REPLACE "@@LERP3@@" "lerp3" CUBIOMES_RNG_H "${CUBIOMES_RNG_H}")
    string(APPEND CUBIOMES_RNG_H "\n#ifndef __cplusplus\n#define lerp cubiomes_rng_lerp\n#endif\n")
    file(WRITE "${_rng}" "${CUBIOMES_RNG_H}")
endfunction()

if(EXISTS "${CUBIOMES_LOCAL_DIR}/finders.c")
    set(CUBIOMES_SRC_DIR "${CUBIOMES_LOCAL_DIR}")
    message(STATUS "Using local cubiomes: ${CUBIOMES_SRC_DIR}")
else()
    include(FetchContent)

    set(FETCHCONTENT_UPDATES_DISCONNECTED ON CACHE BOOL "" FORCE)

    FetchContent_Declare(
            cubiomes
            GIT_REPOSITORY https://github.com/Cubitect/cubiomes.git
            GIT_TAG        e61f90580cbdd883214a8054670dacae655e59c0
            GIT_SHALLOW    TRUE
            # Avoid "git submodule" on Windows (Git for Windows needs MSYS sed/basename in PATH).
            GIT_SUBMODULES ""
    )

    FetchContent_GetProperties(cubiomes)
    if(NOT cubiomes_POPULATED)
        FetchContent_Populate(cubiomes)
    endif()

    set(CUBIOMES_SRC_DIR ${cubiomes_SOURCE_DIR})
    message(STATUS "Using FetchContent cubiomes: ${CUBIOMES_SRC_DIR}")
endif()

# GCC 15 + C++: cubiomes rng.h defines lerp() which conflicts with std::lerp from <cmath>
fortress_finder_patch_cubiomes_rng("${CUBIOMES_SRC_DIR}")

add_library(cubiomes_static STATIC
        ${CUBIOMES_SRC_DIR}/finders.c
        ${CUBIOMES_SRC_DIR}/generator.c
        ${CUBIOMES_SRC_DIR}/layers.c
        ${CUBIOMES_SRC_DIR}/biomenoise.c
        ${CUBIOMES_SRC_DIR}/biomes.c
        ${CUBIOMES_SRC_DIR}/noise.c
        ${CUBIOMES_SRC_DIR}/util.c
        ${CUBIOMES_SRC_DIR}/quadbase.c
)

target_include_directories(cubiomes_static PUBLIC ${CUBIOMES_SRC_DIR})

set_target_properties(cubiomes_static PROPERTIES POSITION_INDEPENDENT_CODE ON)

if(WIN32)
    target_compile_definitions(cubiomes_static PRIVATE _WIN32)
endif()

if(MSVC)
    target_compile_options(cubiomes_static PRIVATE /O2 /DNDEBUG)
else()
    target_compile_options(cubiomes_static PRIVATE -Wall -Wextra -O2 -DNDEBUG)
endif()
