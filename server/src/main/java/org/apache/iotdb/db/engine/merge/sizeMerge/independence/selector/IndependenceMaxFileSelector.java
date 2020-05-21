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

package org.apache.iotdb.db.engine.merge.sizeMerge.independence.selector;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.apache.iotdb.db.engine.merge.sizeMerge.BaseSizeFileSelector;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetadata;

/**
 * IndependenceMaxFileSelector selects the most files from given seqFiles which can be merged as a
 * given time block with single device in a file
 */
public class IndependenceMaxFileSelector extends BaseSizeFileSelector {

  public IndependenceMaxFileSelector(Collection<TsFileResource> seqFiles, long budget) {
    this(seqFiles, budget, Long.MIN_VALUE);
  }

  public IndependenceMaxFileSelector(Collection<TsFileResource> seqFiles, long budget,
      long timeLowerBound) {
    super(seqFiles, budget, timeLowerBound);
  }

  protected boolean isSmallFile(TsFileResource seqFile) throws IOException {
    Map<String, Long> endTimeMap = seqFile.getEndTimeMap();
    if (endTimeMap.size() > 1) {
      return true;
    }
    for (ChunkMetadata chunkMetadata : resource.queryChunkMetadata(seqFile)) {
      if (!this.mergeSizeSelectorStrategy
          .isChunkEnoughLarge(chunkMetadata, minChunkPointNum, timeBlock)) {
        return true;
      }
    }
    return false;
  }
}
