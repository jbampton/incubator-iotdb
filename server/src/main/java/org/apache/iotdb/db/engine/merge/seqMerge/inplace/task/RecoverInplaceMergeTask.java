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

package org.apache.iotdb.db.engine.merge.seqMerge.inplace.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.apache.iotdb.db.engine.merge.IRecoverMergeTask;
import org.apache.iotdb.db.engine.merge.MergeCallback;
import org.apache.iotdb.db.engine.merge.manage.MergeResource;
import org.apache.iotdb.db.engine.merge.seqMerge.inplace.recover.InplaceMergeLogger;
import org.apache.iotdb.db.engine.merge.seqMerge.inplace.recover.LogAnalyzer;
import org.apache.iotdb.db.engine.merge.seqMerge.inplace.recover.LogAnalyzer.Status;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetadata;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.write.writer.RestorableTsFileIOWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RecoverInplaceMergeTask is an extension of MergeTask, which resumes the last merge progress by
 * scanning merge.log using LogAnalyzer and continue the unfinished merge.
 */
public class RecoverInplaceMergeTask extends InplaceMergeTask implements IRecoverMergeTask {

  private static final Logger logger = LoggerFactory.getLogger(RecoverInplaceMergeTask.class);

  private LogAnalyzer analyzer;

  public RecoverInplaceMergeTask(List<TsFileResource> seqFiles,
      List<TsFileResource> unseqFiles, String storageGroupSysDir,
      MergeCallback callback, String taskName,
      boolean fullMerge, String storageGroupName) {
    super(new MergeResource(seqFiles, unseqFiles), storageGroupSysDir, callback, taskName,
        fullMerge,
        storageGroupName);
  }

  public void recoverMerge(boolean continueMerge) throws IOException, MetadataException {
    File logFile = new File(storageGroupSysDir, InplaceMergeLogger.MERGE_LOG_NAME);
    if (!logFile.exists()) {
      logger.info("{} no merge.log, merge recovery ends", taskName);
      return;
    }
    long startTime = System.currentTimeMillis();

    analyzer = new LogAnalyzer(resource, taskName, logFile, storageGroupName);
    Status status = analyzer.analyze();
    if (logger.isInfoEnabled()) {
      logger.info("{} merge recovery status determined: {} after {}ms", taskName, status,
          (System.currentTimeMillis() - startTime));
    }
    switch (status) {
      case NONE:
        logFile.delete();
        break;
      case MERGE_START:
        resumeAfterFilesLogged(continueMerge);
        break;
      case ALL_TS_MERGED:
        resumeAfterAllTsMerged(continueMerge);
        break;
      case MERGE_END:
        cleanUp(continueMerge);
        break;
      default:
        throw new UnsupportedOperationException(taskName + " found unrecognized status " + status);
    }
    if (logger.isInfoEnabled()) {
      logger.info("{} merge recovery ends after {}ms", taskName,
          (System.currentTimeMillis() - startTime));
    }
  }

  private void resumeAfterFilesLogged(boolean continueMerge) throws IOException {
    if (continueMerge) {
      resumeMergeProgress();
      MergeMultiChunkTask mergeChunkTask = new MergeMultiChunkTask(mergeContext, taskName,
          mergeLogger, resource,
          fullMerge, analyzer.getUnmergedPaths());
      analyzer.setUnmergedPaths(null);
      mergeChunkTask.mergeSeries();

      MergeFileTask mergeFileTask = new MergeFileTask(taskName, mergeContext, mergeLogger, resource,
          resource.getSeqFiles());
      mergeFileTask.mergeFiles();
    }
    cleanUp(continueMerge);
  }

  private void resumeAfterAllTsMerged(boolean continueMerge) throws IOException {
    if (continueMerge) {
      resumeMergeProgress();
      MergeFileTask mergeFileTask = new MergeFileTask(taskName, mergeContext, mergeLogger, resource,
          analyzer.getUnmergedFiles());
      analyzer.setUnmergedFiles(null);
      mergeFileTask.mergeFiles();
    } else {
      // NOTICE: although some of the seqFiles may have been truncated in last merge, we do not
      // recover them here because later TsFile recovery will recover them
      truncateFiles();
    }
    cleanUp(continueMerge);
  }

  private void resumeMergeProgress() throws IOException {
    mergeLogger = new InplaceMergeLogger(storageGroupSysDir);
    truncateFiles();
    recoverChunkCounts();
  }

  // scan the metadata to compute how many chunks are merged/unmerged so at last we can decide to
  // move the merged chunks or the unmerged chunks
  private void recoverChunkCounts() throws IOException {
    logger.info("{} recovering chunk counts", taskName);
    int fileCnt = 1;
    for (TsFileResource tsFileResource : resource.getSeqFiles()) {
      logger.info("{} recovering {}  {}/{}", taskName, tsFileResource.getFile().getName(),
          fileCnt, resource.getSeqFiles().size());
      RestorableTsFileIOWriter mergeFileWriter = resource.getMergeFileWriter(tsFileResource);
      mergeFileWriter.makeMetadataVisible();
      mergeContext.getUnmergedChunkStartTimes().put(tsFileResource, new HashMap<>());
      List<Path> pathsToRecover = analyzer.getMergedPaths();
      int cnt = 0;
      double progress = 0.0;
      for (Path path : pathsToRecover) {
        recoverChunkCounts(path, tsFileResource, mergeFileWriter);
        if (logger.isInfoEnabled()) {
          cnt += 1.0;
          double newProgress = 100.0 * cnt / pathsToRecover.size();
          if (newProgress - progress >= 1.0) {
            progress = newProgress;
            logger.info("{} {}% series count of {} are recovered", taskName, progress,
                tsFileResource.getFile().getName());
          }
        }
      }
      fileCnt++;
    }
    analyzer.setMergedPaths(null);
  }

  private void recoverChunkCounts(Path path, TsFileResource tsFileResource,
      RestorableTsFileIOWriter mergeFileWriter) throws IOException {
    mergeContext.getUnmergedChunkStartTimes().get(tsFileResource).put(path, new ArrayList<>());

    List<ChunkMetadata> seqFileChunks = resource.queryChunkMetadata(path, tsFileResource);
    List<ChunkMetadata> mergeFileChunks =
        mergeFileWriter.getVisibleMetadataList(path.getDevice(), path.getMeasurement(), null);
    mergeContext.getMergedChunkCnt().compute(tsFileResource, (k, v) -> v == null ?
        mergeFileChunks.size() : v + mergeFileChunks.size());
    int seqChunkIndex = 0;
    int mergeChunkIndex = 0;
    int unmergedCnt = 0;
    while (seqChunkIndex < seqFileChunks.size() && mergeChunkIndex < mergeFileChunks.size()) {
      ChunkMetadata seqChunk = seqFileChunks.get(seqChunkIndex);
      ChunkMetadata mergedChunk = mergeFileChunks.get(mergeChunkIndex);
      if (seqChunk.getStartTime() < mergedChunk.getStartTime()) {
        // this seqChunk is unmerged
        unmergedCnt++;
        seqChunkIndex++;
        mergeContext.getUnmergedChunkStartTimes().get(tsFileResource).get(path)
            .add(seqChunk.getStartTime());
      } else if (mergedChunk.getStartTime() <= seqChunk.getStartTime() &&
          seqChunk.getStartTime() <= mergedChunk.getEndTime()) {
        // this seqChunk is merged
        seqChunkIndex++;
      } else {
        // seqChunk.startTime > mergeChunk.endTime, find next mergedChunk that may cover the
        // seqChunk
        mergeChunkIndex++;
      }
    }
    int finalUnmergedCnt = unmergedCnt;
    mergeContext.getUnmergedChunkCnt().compute(tsFileResource, (k, v) -> v == null ?
        finalUnmergedCnt : v + finalUnmergedCnt);
  }

  private void truncateFiles() throws IOException {
    logger.info("{} truncating {} files", taskName, analyzer.getFileLastPositions().size());
    for (Entry<File, Long> entry : analyzer.getFileLastPositions().entrySet()) {
      File file = entry.getKey();
      Long lastPosition = entry.getValue();
      if (file.exists() && file.length() != lastPosition) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
          FileChannel channel = fileInputStream.getChannel();
          channel.truncate(lastPosition);
          channel.close();
        }
      }
    }
    analyzer.setFileLastPositions(null);
  }
}
