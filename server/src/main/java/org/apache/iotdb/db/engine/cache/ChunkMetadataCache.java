/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.engine.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.adapter.IoTDBConfigDynamicAdapter;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.query.control.FileReaderManager;
import org.apache.iotdb.db.utils.FileLoaderUtils;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetadata;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.utils.BloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to cache <code>List<ChunkMetaData></code> of tsfile in IoTDB. The caching
 * strategy is LRU.
 */
public class ChunkMetadataCache {

  private static final Logger logger = LoggerFactory.getLogger(ChunkMetadataCache.class);
  private static final IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();
  private static final long MEMORY_THRESHOLD_IN_B = config.getAllocateMemoryForChunkMetaDataCache();
  private static boolean cacheEnable = config.isMetaDataCacheEnable();
  /**
   * key: file path dot deviceId dot sensorId.
   * <p>
   * value: chunkMetaData list of one timeseries in the file.
   */
  private final LRULinkedHashMap<String, List<ChunkMetadata>> lruCache;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private AtomicLong cacheHitNum = new AtomicLong();
  private AtomicLong cacheRequestNum = new AtomicLong();


  private ChunkMetadataCache(long memoryThreshold) {
    logger.info("ChunkMetadataCache size = " + memoryThreshold);
    lruCache = new LRULinkedHashMap<String, List<ChunkMetadata>>(memoryThreshold, true) {
      int count = 0;
      long averageChunkMetadataSize = 0;

      @Override
      protected long calEntrySize(String key, List<ChunkMetadata> value) {
        if (value.isEmpty()) {
          return key.getBytes().length + averageChunkMetadataSize * value.size();
        }

        if (count < 10) {
          long currentSize = RamUsageEstimator.sizeOf(value.get(0));
          averageChunkMetadataSize = ((averageChunkMetadataSize * count) + currentSize) / (++count);
          IoTDBConfigDynamicAdapter.setChunkMetadataSizeInByte(averageChunkMetadataSize);
          return key.getBytes().length + currentSize * value.size();
        } else if (count < 100000) {
          count++;
          return key.getBytes().length + averageChunkMetadataSize * value.size();
        } else {
          averageChunkMetadataSize = RamUsageEstimator.sizeOf(value.get(0));
          count = 1;
          return key.getBytes().length + averageChunkMetadataSize * value.size();
        }
      }
    };
  }

  public static ChunkMetadataCache getInstance() {
    return ChunkMetadataCacheSingleton.INSTANCE;
  }

  /**
   * get {@link ChunkMetadata}. THREAD SAFE.
   */
  public List<ChunkMetadata> get(String filePath, Path seriesPath)
      throws IOException {
    if (!cacheEnable) {
      // bloom filter part
      TsFileSequenceReader tsFileReader = FileReaderManager.getInstance().get(filePath, true);
      BloomFilter bloomFilter = tsFileReader.readBloomFilter();
      if (bloomFilter != null && !bloomFilter.contains(seriesPath.getFullPath())) {
        if (logger.isDebugEnabled()) {
          logger.debug(String
              .format("path not found by bloom filter, file is: %s, path is: %s", filePath, seriesPath));
        }
        return new ArrayList<>();
      }
      // If timeseries isn't included in the tsfile, empty list is returned.
      return tsFileReader.getChunkMetadataList(seriesPath);
    }

    String key = (filePath + IoTDBConstant.PATH_SEPARATOR
        + seriesPath.getDevice() + seriesPath.getMeasurement()).intern();

    cacheRequestNum.incrementAndGet();

    lock.readLock().lock();
    try {
      if (lruCache.containsKey(key)) {
        cacheHitNum.incrementAndGet();
        printCacheLog(true);
        return new ArrayList<>(lruCache.get(key));
      }
    } finally {
      lock.readLock().unlock();
    }

    lock.writeLock().lock();
    try {
      if (lruCache.containsKey(key)) {
        printCacheLog(true);
        cacheHitNum.incrementAndGet();
        return new ArrayList<>(lruCache.get(key));
      }
      printCacheLog(false);
      // bloom filter part
      TsFileSequenceReader tsFileReader = FileReaderManager.getInstance().get(filePath, true);
      BloomFilter bloomFilter = tsFileReader.readBloomFilter();
      if (bloomFilter != null && !bloomFilter.contains(seriesPath.getFullPath())) {
        return new ArrayList<>();
      }
      List<ChunkMetadata> chunkMetaDataList = FileLoaderUtils
          .getChunkMetadataList(seriesPath, filePath);
      lruCache.put(key, chunkMetaDataList);
      return chunkMetaDataList;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void printCacheLog(boolean isHit) {
    if (!logger.isDebugEnabled()) {
      return;
    }
    logger.debug(
        "[ChunkMetaData cache {}hit] The number of requests for cache is {}, hit rate is {}.",
        isHit ? "" : "didn't ", cacheRequestNum.get(),
        cacheHitNum.get() * 1.0 / cacheRequestNum.get());
  }

  double calculateChunkMetaDataHitRatio() {
    if (cacheRequestNum.get() != 0) {
      return cacheHitNum.get() * 1.0 / cacheRequestNum.get();
    } else {
      return 0;
    }
  }

  /**
   * clear LRUCache.
   */
  public void clear() {
    synchronized (lruCache) {
      lruCache.clear();
    }
  }

  public void remove(TsFileResource resource) {
    synchronized (lruCache) {
      lruCache.entrySet().removeIf(e -> e.getKey().startsWith(resource.getPath()));
    }
  }

  /**
   * singleton pattern.
   */
  private static class ChunkMetadataCacheSingleton {

    private static final ChunkMetadataCache INSTANCE = new
        ChunkMetadataCache(MEMORY_THRESHOLD_IN_B);
  }
}