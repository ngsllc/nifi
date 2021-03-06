/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.integration.flowfilerepo;

import org.apache.nifi.controller.queue.FlowFileQueue;
import org.apache.nifi.controller.repository.RepositoryRecord;
import org.apache.nifi.controller.repository.RepositoryRecordSerdeFactory;
import org.apache.nifi.controller.repository.StandardRepositoryRecordSerdeFactory;
import org.apache.nifi.controller.repository.WriteAheadFlowFileRepository;
import org.apache.nifi.controller.repository.claim.ResourceClaimManager;
import org.apache.nifi.util.NiFiProperties;
import org.wali.SerDe;
import org.wali.UpdateType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

public class OOMEWriteAheadFlowFileRepository extends WriteAheadFlowFileRepository {

    public OOMEWriteAheadFlowFileRepository() {
    }

    public OOMEWriteAheadFlowFileRepository(final NiFiProperties nifiProperties) {
        super(nifiProperties);
    }

    @Override
    public void initialize(final ResourceClaimManager claimManager) throws IOException {
        super.initialize(claimManager, new ThrowOOMERepositoryRecordSerdeFactory(new StandardRepositoryRecordSerdeFactory(claimManager)));
    }


    private static class ThrowOOMERepositoryRecordSerdeFactory implements RepositoryRecordSerdeFactory {
        private final RepositoryRecordSerdeFactory factory;

        public ThrowOOMERepositoryRecordSerdeFactory(final RepositoryRecordSerdeFactory factory) {
            this.factory = factory;
        }

        @Override
        public void setQueueMap(final Map<String, FlowFileQueue> queueMap) {
            factory.setQueueMap(queueMap);
        }

        @Override
        public SerDe<RepositoryRecord> createSerDe(final String encodingName) {
            final SerDe<RepositoryRecord> serde = factory.createSerDe(encodingName);
            return new ThrowOOMESerde(serde, 3);
        }

        @Override
        public Long getRecordIdentifier(final RepositoryRecord record) {
            return factory.getRecordIdentifier(record);
        }

        @Override
        public UpdateType getUpdateType(final RepositoryRecord record) {
            return factory.getUpdateType(record);
        }

        @Override
        public String getLocation(final RepositoryRecord record) {
            return factory.getLocation(record);
        }
    }


    private static class ThrowOOMESerde implements SerDe<RepositoryRecord> {
        private final SerDe<RepositoryRecord> serde;
        private final int afterSuccessfulAttempts;
        private int successfulUpdates = 0;

        public ThrowOOMESerde(final SerDe<RepositoryRecord> serde, final int afterSuccessfulAttempts) {
            this.serde = serde;
            this.afterSuccessfulAttempts = afterSuccessfulAttempts;
        }

        @Override
        public void serializeEdit(final RepositoryRecord previousRecordState, final RepositoryRecord newRecordState, final DataOutputStream out) throws IOException {
            if (successfulUpdates++ == afterSuccessfulAttempts) {
                throw new OutOfMemoryError("Intentional OOME for unit test");
            }

            serde.serializeEdit(previousRecordState, newRecordState, out);
        }

        @Override
        public void serializeRecord(final RepositoryRecord record, final DataOutputStream out) throws IOException {
            if (successfulUpdates++ == afterSuccessfulAttempts) {
                throw new OutOfMemoryError("Intentional OOME for unit test");
            }

            serde.serializeRecord(record, out);
        }

        @Override
        public RepositoryRecord deserializeEdit(final DataInputStream in, final Map<Object, RepositoryRecord> currentRecordStates, final int version) throws IOException {
            return serde.deserializeEdit(in, currentRecordStates, version);
        }

        @Override
        public RepositoryRecord deserializeRecord(final DataInputStream in, final int version) throws IOException {
            return serde.deserializeRecord(in, version);
        }

        @Override
        public Object getRecordIdentifier(final RepositoryRecord record) {
            return serde.getRecordIdentifier(record);
        }

        @Override
        public UpdateType getUpdateType(final RepositoryRecord record) {
            return serde.getUpdateType(record);
        }

        @Override
        public String getLocation(final RepositoryRecord record) {
            return serde.getLocation(record);
        }

        @Override
        public int getVersion() {
            return serde.getVersion();
        }
    }
}
