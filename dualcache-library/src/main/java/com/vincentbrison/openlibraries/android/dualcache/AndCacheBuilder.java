package com.vincentbrison.openlibraries.android.dualcache;

import android.content.Context;

import java.io.File;
import java.io.Serializable;

/**
 * Class used to build a cache.
 */
public class AndCacheBuilder {

    /**
     * Defined the sub folder from {@link Context#getCacheDir()} used to store all
     * the data generated from the use of this library.
     */
    private static final String CACHE_FILE_PREFIX = "andcache";

    private String id;
    private int appVersion;
    private boolean logEnabled;
    private int maxRamSizeBytes;
    private DualCacheRamMode ramMode;
    private CacheSerializer<Serializable> ramSerializer;
    private SizeOf<Serializable> sizeOf;
    private int maxDiskSizeBytes;
    private File diskFolder;
    private boolean usePrivateFiles = true;
    private boolean noDisk;

    /**
     * Start the building of the cache.
     *
     * @param id         is the id of the cache (should be unique).
     * @param appVersion is the app version of the app. If data are already stored in disk cache
     *                   with previous app version, it will be invalidate.
     */
    public AndCacheBuilder(String id, int appVersion) {
        this.id = id;
        this.appVersion = appVersion;
        this.ramMode = null;
        this.logEnabled = false;
        this.maxDiskSizeBytes = 100 * 1024 * 1024;
    }

    /**
     * Enabling log from the cache. By default disable.
     *
     * @return the builder.
     */
    public AndCacheBuilder enableLog() {
        this.logEnabled = true;
        return this;
    }

    /**
     * Builder the cache. Exception will be thrown if it can not be created.
     *
     * @param context use to access disk cache
     * @return the cache instance.
     */
    public AndCache build(Context context) {
        if (ramMode == null) {
            throw new IllegalStateException("No ram mode set");
        }

        if(!noDisk && null == diskFolder) {
            diskFolder = getDefaultDiskCacheFolder(usePrivateFiles , context);
        }

        return new AndCache(
                appVersion,
                new Logger(logEnabled),
                ramMode,
                ramSerializer,
                maxRamSizeBytes,
                sizeOf,
                noDisk,
                maxDiskSizeBytes,
                diskFolder
        );
    }

    /**
     * Use Json serialization/deserialization to store and retrieve object from ram cache.
     *
     * @param maxRamSizeBytes is the max amount of ram in bytes which can be used by the ram cache.
     * @param serializer      is the cache interface which provide serialization/deserialization
     *                        methods
     *                        for the ram cache layer.
     * @return the builder.
     */
    public AndCacheBuilder useSerializerInRam(
        int maxRamSizeBytes, CacheSerializer<Serializable> serializer
    ) {
        this.ramMode = DualCacheRamMode.ENABLE_WITH_SPECIFIC_SERIALIZER;
        this.maxRamSizeBytes = maxRamSizeBytes;
        this.ramSerializer = serializer;
        return this;
    }

    /**
     * Store directly objects in ram (without serialization/deserialization).
     * You have to provide a way to compute the size of an object in
     * ram to be able to used the LRU capacity of the ram cache.
     *
     * @param maxRamSizeBytes is the max amount of ram which can be used by the ram cache.
     * @param handlerSizeOf   computes the size of object stored in ram.
     * @return the builder.
     */
    public AndCacheBuilder useReferenceInRam(
        int maxRamSizeBytes, SizeOf<Serializable> handlerSizeOf
    ) {
        this.ramMode = DualCacheRamMode.ENABLE_WITH_REFERENCE;
        this.maxRamSizeBytes = maxRamSizeBytes;
        this.sizeOf = handlerSizeOf;
        return this;
    }

    /**
     * The max size of disk in bytes which can be used by the disk cache
     *
     * @param bytes disk cache size in bytes
     * @return the builder
     */
    public AndCacheBuilder maxDiskSize(int bytes) {
        this.maxDiskSizeBytes = bytes;
        return this;
    }

    /**
     * Whether use default disk cache folder;
     *
     * @param usePrivateFiles  is true if you want to use {@link Context#MODE_PRIVATE} with the
     *                         default disk cache folder.
     * @return
     */
    public AndCacheBuilder usePrivateFiles(boolean usePrivateFiles) {
        this.usePrivateFiles = usePrivateFiles;
        return this;
    }

    private File getDefaultDiskCacheFolder(boolean usePrivateFiles, Context context) {
        File folder;
        if (usePrivateFiles) {
            folder = context.getDir(
                CACHE_FILE_PREFIX + this.id,
                Context.MODE_PRIVATE
            );
        } else {
            folder = new File(context.getCacheDir().getPath()
                                  + "/" + CACHE_FILE_PREFIX
                                  + "/" + this.id
            );
        }
        return folder;
    }

    /**
     * Use this if you do not want use the disk cache layer, meaning that only the ram cache layer
     * will be used.
     *
     * @return the builder.
     */
    public AndCacheBuilder noDisk() {
        noDisk = true;
        return this;
    }
}
