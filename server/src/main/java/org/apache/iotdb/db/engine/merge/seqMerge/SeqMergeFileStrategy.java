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

package org.apache.iotdb.db.engine.merge.seqMerge;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.engine.merge.IMergeFileSelector;
import org.apache.iotdb.db.engine.merge.IRecoverMergeTask;
import org.apache.iotdb.db.engine.merge.MergeCallback;
import org.apache.iotdb.db.engine.merge.seqMerge.inplace.selector.InplaceMaxFileSelector;
import org.apache.iotdb.db.engine.merge.seqMerge.inplace.task.InplaceMergeTask;
import org.apache.iotdb.db.engine.merge.seqMerge.inplace.task.RecoverInplaceMergeTask;
import org.apache.iotdb.db.engine.merge.manage.MergeResource;
import org.apache.iotdb.db.engine.merge.seqMerge.squeeze.selector.SqueezeMaxFileSelector;
import org.apache.iotdb.db.engine.merge.seqMerge.squeeze.task.RecoverSqueezeMergeTask;
import org.apache.iotdb.db.engine.merge.seqMerge.squeeze.task.SqueezeMergeTask;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;

public enum SeqMergeFileStrategy {
  INPLACE,
  SQUEEZE;
  // TODO new strategies?

  public IMergeFileSelector getFileSelector(Collection<TsFileResource> seqFiles,
      Collection<TsFileResource> unseqFiles, long budget, long timeLowerBound) {
    switch (this) {
      case INPLACE:
        return new InplaceMaxFileSelector(seqFiles, unseqFiles, budget, timeLowerBound);
      case SQUEEZE:
      default:
        return new SqueezeMaxFileSelector(seqFiles, unseqFiles, budget, timeLowerBound);
    }
  }

  public Callable<Void> getMergeTask(MergeResource mergeResource, String storageGroupSysDir,
      MergeCallback callback, String taskName, String storageGroupName,
      boolean isFullMerge) {
    switch (this) {
      case INPLACE:
        return new InplaceMergeTask(mergeResource, storageGroupSysDir, callback, taskName,
            isFullMerge, storageGroupName);
      case SQUEEZE:
        return new SqueezeMergeTask(mergeResource, storageGroupSysDir, callback, taskName,
            storageGroupName);
    }
    return null;
  }

  public IRecoverMergeTask getRecoverMergeTask(List<TsFileResource> seqTsFiles,
      List<TsFileResource> unseqTsFiles, String storageGroupSysDir, MergeCallback callback,
      String taskName, String storageGroupName) {
    switch (this) {
      case SQUEEZE:
        return new RecoverSqueezeMergeTask(seqTsFiles,
            unseqTsFiles, storageGroupSysDir, callback, taskName, storageGroupName);
      case INPLACE:
      default:
        return new RecoverInplaceMergeTask(seqTsFiles,
            unseqTsFiles, storageGroupSysDir, callback, taskName,
            IoTDBDescriptor.getInstance().getConfig().isForceFullMerge(), storageGroupName);
    }
  }
}
