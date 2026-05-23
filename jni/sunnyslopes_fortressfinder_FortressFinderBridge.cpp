#include "generated/sunnyslopes_fortressfinder_FortressFinderBridge.h"

#include <vector>

#define FORTRESS_FINDER_JNI
#include "../fortress_finder_JNI.cpp"

static Progress progress{};
static ThreadSafeResults<FortressHit> globalResults{};
static int g_lastSearchMode = 0;

static std::vector<jint> hitsToFlatInts(const std::vector<FortressHit> &hits, int mode) {
    std::vector<jint> flat;
    if (mode == 1) {
        flat.reserve(hits.size() * 7);
        for (const FortressHit &h: hits) {
            flat.push_back(h.chunkX);
            flat.push_back(h.chunkZ);
            flat.push_back(h.outX);
            flat.push_back(h.outY);
            flat.push_back(h.outZ);
            flat.push_back(h.longEdge);
            flat.push_back(h.shortEdge);
        }
    } else {
        flat.reserve(hits.size() * 6);
        for (const FortressHit &h: hits) {
            flat.push_back(h.chunkX);
            flat.push_back(h.chunkZ);
            flat.push_back(h.outX);
            flat.push_back(h.outY);
            flat.push_back(h.outZ);
            flat.push_back(h.shapeCode);
        }
    }
    return flat;
}

static jintArray makeIntArray(JNIEnv *env, const std::vector<jint> &data) {
    if (data.empty())
        return nullptr;
    jsize len = (jsize) data.size();
    jintArray arr = env->NewIntArray(len);
    if (arr == nullptr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, len, data.data());
    return arr;
}

JNIEXPORT jintArray JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_fortressSearch
  (JNIEnv *env, jclass, jint mode, jlong seed, jint centerX, jint centerZ, jint r,
   jint mc, jint crossFilter, jint minLong, jint minShort, jint numThreads) {
    g_lastSearchMode = mode;

    SearchConfig cfg;
    cfg.mode = mode;
    cfg.seed = (uint64_t) seed;
    cfg.centerX = centerX;
    cfg.centerZ = centerZ;
    cfg.r = r;
    cfg.mc = mc;
    cfg.crossFilter = crossFilter;
    cfg.minLong = minLong;
    cfg.minShort = minShort;

    globalResults.clear();
    progress.current = 0;
    progress.total = 1;
    progress.chunkInRunning = 0;
    progress.phase1 = 1;
    progress.try_pause = false;
    progress.try_stop = false;

    runFortressSearch(cfg, &progress, globalResults, numThreads);

    progress.try_pause = false;
    if (progress.try_stop.load())
        return nullptr;

    auto hits = globalResults.getAllResults();
    if (hits.empty())
        return nullptr;

    progress.phase1 = -1;
    return makeIntArray(env, hitsToFlatInts(hits, mode));
}

JNIEXPORT jintArray JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_getSearchProgress
  (JNIEnv *env, jclass) {
    if (progress.total.load() == 0)
        return nullptr;

    int status = 0;
    if (progress.phase1.load() == 1) {
        if (progress.chunkInRunning.load() == 0)
            status = 1;
        else
            status = 0;
    } else if (progress.phase1.load() == 2) {
        if (progress.current.load() >= progress.total.load())
            status = 2;
        else
            status = 1;
    }

    jint data[4] = {
        progress.current.load(),
        progress.total.load(),
        progress.phase1.load(),
        status,
    };
    jintArray arr = env->NewIntArray(4);
    if (arr == nullptr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, 4, data);
    return arr;
}

JNIEXPORT jintArray JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_getNowResult
  (JNIEnv *env, jclass) {
    if (globalResults.empty())
        return nullptr;
    auto hits = globalResults.peekAll();
    return makeIntArray(env, hitsToFlatInts(hits, g_lastSearchMode));
}

JNIEXPORT jboolean JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_pause
  (JNIEnv *, jclass) {
    progress.try_pause = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_resume
  (JNIEnv *, jclass) {
    progress.try_pause = false;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_stop
  (JNIEnv *, jclass) {
    progress.try_stop = true;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_sunnyslopes_fortressfinder_FortressFinderBridge_resetSearchState
  (JNIEnv *, jclass) {
    progress.try_pause = false;
    progress.try_stop = false;
}
