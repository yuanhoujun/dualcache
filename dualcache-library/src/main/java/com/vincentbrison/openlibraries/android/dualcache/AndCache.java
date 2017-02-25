package com.vincentbrison.openlibraries.android.dualcache;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * 这个类旨在提供一个简单的，稳定的，容易使用的二级缓存实现。
 *
 * @author Scott Smith 2017-02-25 13:38
 */
public class AndCache {
    private static final int VALUES_PER_CACHE_ENTRY = 1;

    private final RamLruCache ramCacheLru;
    private DiskLruCache diskLruCache;
    private final int maxDiskSizeBytes;
    private final File diskCacheFolder;
    private final int appVersion;
    private final DualCacheRamMode ramMode;
    private final CacheSerializer<Serializable> ramSerializer;
    private final DualCacheLock dualCacheLock = new DualCacheLock();
    private final Logger logger;
    private final LoggerHelper loggerHelper;
    private boolean noDisk;

    public AndCache(
            int appVersion,
            Logger logger,
            DualCacheRamMode ramMode,
            CacheSerializer<Serializable> ramSerializer,
            int maxRamSizeBytes,
            SizeOf<Serializable> sizeOf,
            boolean noDisk,
            int maxDiskSizeBytes,
            File diskFolder
    ) {
        this.appVersion = appVersion;
        this.ramMode = ramMode;
        this.ramSerializer = ramSerializer;
        this.diskCacheFolder = diskFolder;
        this.logger = logger;
        this.loggerHelper = new LoggerHelper(logger);
        this.noDisk = noDisk;

        switch (ramMode) {
            case ENABLE_WITH_SPECIFIC_SERIALIZER:
                this.ramCacheLru = new StringLruCache(maxRamSizeBytes);
                break;
            case ENABLE_WITH_REFERENCE:
                this.ramCacheLru = new ReferenceLruCache<>(maxRamSizeBytes, sizeOf);
                break;
            default:
                this.ramCacheLru = null;
        }

        if(!noDisk) {
            this.maxDiskSizeBytes = maxDiskSizeBytes;
            try {
                openDiskLruCache(diskFolder);
            } catch (IOException e) {
                logger.logError(e);
            }
        } else {
            this.maxDiskSizeBytes = 0;
        }
    }

    private void openDiskLruCache(File diskFolder) throws IOException {
        this.diskLruCache = DiskLruCache.open(
                diskFolder,
                this.appVersion,
                VALUES_PER_CACHE_ENTRY,
                this.maxDiskSizeBytes
        );
    }

    public long getRamUsedInBytes() {
        if (ramCacheLru == null) {
            return -1;
        } else {
            return ramCacheLru.size();
        }
    }

    public long getDiskUsedInBytes() {
        if (diskLruCache == null) {
            return -1;
        } else {
            return diskLruCache.size();
        }

    }

    /**
     * Return the way objects are cached in RAM layer.
     *
     * @return the way objects are cached in RAM layer.
     */
    public DualCacheRamMode getRAMMode() {
        return ramMode;
    }

    /**
     * Return the object of the corresponding key from the cache. In no object is available,
     * return null.
     *
     * @param key is the key of the object.
     * @return the object of the corresponding key from the cache. In no object is available,
     * return null.
     */
    public <T extends Serializable> T get(String key) {
        Object ramResult = null;
        Serializable result = null;
        DiskLruCache.Snapshot snapshotObject = null;

        // Try to get the object from RAM.
        boolean isRamSerialized = ramMode.equals(DualCacheRamMode.ENABLE_WITH_SPECIFIC_SERIALIZER);
        boolean isRamReferenced = ramMode.equals(DualCacheRamMode.ENABLE_WITH_REFERENCE);
        if (isRamSerialized || isRamReferenced) {
            ramResult = ramCacheLru.get(key);
        }

        if (ramResult == null) {
            if(!noDisk) {
                // Try to get the cached object from disk.
                loggerHelper.logEntryForKeyIsNotInRam(key);
                try {
                    dualCacheLock.lockDiskEntryWrite(key);
                    snapshotObject = diskLruCache.get(key);
                } catch (IOException e) {
                    logger.logError(e);
                } finally {
                    dualCacheLock.unLockDiskEntryWrite(key);
                }

                if (snapshotObject != null) {
                    loggerHelper.logEntryForKeyIsOnDisk(key);
                    try {
                        result = snapshotObject.getSerializable(0);
                    } catch (IOException | ClassNotFoundException e) {
                        logger.logError(e);
                    }
                } else {
                    loggerHelper.logEntryForKeyIsNotOnDisk(key);
                }
            }
        } else {
            loggerHelper.logEntryForKeyIsInRam(key);
            if(ramMode.equals(DualCacheRamMode.ENABLE_WITH_SPECIFIC_SERIALIZER)) {
                result = ramSerializer.fromString((String) ramResult);
            } else {
                result = (Serializable) ramResult;
            }
        }

        return (T) result;
    }

    /**
     * Delete the corresponding object in cache.
     *
     * @param key is the key of the object.
     */
    public void delete(String key) {
        if (!ramMode.equals(DualCacheRamMode.DISABLE)) {
            ramCacheLru.remove(key);
        }
        if(!noDisk) {
            try {
                dualCacheLock.lockDiskEntryWrite(key);
                diskLruCache.remove(key);
            } catch (IOException e) {
                logger.logError(e);
            } finally {
                dualCacheLock.unLockDiskEntryWrite(key);
            }
        }
    }

    /**
     * Remove all objects from cache (both RAM and disk).
     */
    public void invalidate() {
        invalidateDisk();
        invalidateRAM();
    }

    /**
     * Remove all objects from RAM.
     */
    public void invalidateRAM() {
        if (!ramMode.equals(DualCacheRamMode.DISABLE)) {
            ramCacheLru.evictAll();
        }
    }

    /**
     * Remove all objects from Disk.
     */
    public void invalidateDisk() {
        if(!noDisk) {
            try {
                dualCacheLock.lockFullDiskWrite();
                diskLruCache.delete();
                openDiskLruCache(diskCacheFolder);
            } catch (IOException e) {
                logger.logError(e);
            } finally {
                dualCacheLock.unLockFullDiskWrite();
            }
        }
    }

    /**
     * Test if an object is present in cache.
     * @param key is the key of the object.
     * @return true if the object is present in cache, false otherwise.
     */
    public boolean contains(String key) {
        if (!ramMode.equals(DualCacheRamMode.DISABLE) && ramCacheLru.snapshot().containsKey(key)) {
            return true;
        }
        if(!noDisk) {
            try {
                dualCacheLock.lockDiskEntryWrite(key);
                if (diskLruCache.get(key) != null) {
                    return true;
                }
            } catch (IOException e) {
                logger.logError(e);
            } finally {
                dualCacheLock.unLockDiskEntryWrite(key);
            }
        }
        return false;
    }

    /**
     * Put a value into cache
     *
     * @param key    key used to find a value
     * @param value  value
     * @param <T>    the type of value
     */
    public <T extends Serializable> void put(String key , T value) {
        // Synchronize put on each entry. Gives concurrent editions on different entries, and atomic
        // modification on the same entry.
        if (ramMode.equals(DualCacheRamMode.ENABLE_WITH_REFERENCE)) {
            ramCacheLru.put(key, value);
        }

        String ramSerialized = null;
        if (ramMode.equals(DualCacheRamMode.ENABLE_WITH_SPECIFIC_SERIALIZER)) {
            ramSerialized = ramSerializer.toString(value);
            ramCacheLru.put(key, ramSerialized);
        }

        if(!noDisk) {
            try {
                dualCacheLock.lockDiskEntryWrite(key);
                DiskLruCache.Editor editor = diskLruCache.edit(key);
                editor.set(0, value);
                editor.commit();
            } catch (IOException e) {
                logger.logError(e);
            } finally {
                dualCacheLock.unLockDiskEntryWrite(key);
            }
        }
    }

    /**
     * The max size of disk in bytes which can be used by the disk cache
     *
     * @return max size of disk cache
     */
    public int maxDiskCacheSize() {
        if(!noDisk) {
            return maxDiskSizeBytes;
        } else {
            return -1;
        }
    }

    /**
     * 获取最大可用的Ram缓存大小
     *
     * @return 最大Ram缓存大小
     */
    public int maxRamCacheSize() {
        return ramCacheLru.maxSize();
    }
}
