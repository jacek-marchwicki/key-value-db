/*
 * Copyright 2015 Jacek Marchwicki <jacek.marchwicki@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appunite.keyvalue.driver.level;

import com.appunite.keyvalue.KeyValue;
import com.appunite.keyvalue.NotFoundException;
import com.appunite.keyvalue.driver.level.internal.Preconditions;
import com.appunite.leveldb.KeyNotFoundException;
import com.appunite.leveldb.LevelDB;
import com.appunite.leveldb.LevelDBException;
import com.appunite.leveldb.LevelIterator;
import com.appunite.leveldb.WriteBatch;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class KeyValueLevel implements KeyValue {
    @Nonnull
    private final LevelDB db;


    public KeyValueLevel(@Nonnull LevelDB levelDB) {
        db = levelDB;
    }

    @Nonnull
    public static KeyValueLevel create(@Nonnull File path) throws IOException, LevelDBException {
        return new KeyValueLevel(createDb(path));
    }

    @Deprecated
    public KeyValueLevel(@Nonnull File path) {
        this(createDbOrFail(path));
    }

    @Deprecated
    @Nonnull
    private static LevelDB createDbOrFail(@Nonnull File path) {
        try {
            return createDb(path);
        } catch (LevelDBException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    private static LevelDB createDb(@Nonnull File path) throws IOException, LevelDBException {
        if (!path.isDirectory() && !path.mkdirs()) {
            throw new IOException("Could not create directory");
        }
        return new LevelDB(path.getAbsolutePath());
    }

    @Override
    public void put(@Nonnull ByteString key, @Nonnull ByteString value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        try {
            db.putBytes(key.toByteArray(), value.toByteArray());
        } catch (LevelDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void del(@Nonnull ByteString key) {
        Preconditions.checkNotNull(key);
        try {
            db.delete(key.toByteArray());
        } catch (LevelDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public Batch newBatch() {
        return new BatchLevel(db);
    }

    private static class BatchLevel implements Batch {

        @Nonnull
        private final LevelDB keyValueLevel;
        @Nonnull
        private final WriteBatch writeBatch;

        BatchLevel(@Nonnull LevelDB keyValueLevel) {
            this.keyValueLevel = keyValueLevel;
            writeBatch = new WriteBatch();
        }

        @Override
        public void put(@Nonnull ByteString key, @Nonnull ByteString value) {
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(value);
            try {
                writeBatch.putBytes(key.toByteArray(), value.toByteArray());
            } catch (LevelDBException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void del(@Nonnull ByteString key) {
            Preconditions.checkNotNull(key);
            try {
                writeBatch.delete(key.toByteArray());
            } catch (LevelDBException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clear() {
            writeBatch.clear();
        }

        @Override
        public void write() {
            try {
                keyValueLevel.write(writeBatch);
            } catch (LevelDBException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nonnull
    @Override
    public ByteString getBytes(@Nonnull ByteString key) throws NotFoundException {
        Preconditions.checkNotNull(key);
        try {
            return ByteString.copyFrom(db.getBytes(key.toByteArray()));
        } catch (LevelDBException e) {
            throw new RuntimeException(e);
        } catch (KeyNotFoundException e) {
            throw new NotFoundException();
        }
    }

    @Nonnull
    @Override
    public Iterator getKeys(@Nonnull final ByteString prefix, @Nullable final ByteString nextTokenOrNull, final int batch) {
        return fetchValues(prefix, nextTokenOrNull, batch);
    }

    @Nonnull
    @Override
    public Iterator fetchValues(@Nonnull final ByteString prefix, @Nullable final ByteString nextTokenOrNull, final int batch) {
        Preconditions.checkNotNull(prefix);
        Preconditions.checkArgument(batch >= 1);
        final int batchQuery = Math.min(batch, 1000);
        final ArrayList<ByteString> arrayList = new ArrayList<>(batchQuery);
        final ByteString startWith = nextTokenOrNull == null ? prefix : nextTokenOrNull;
        try {
            final LevelIterator iterator = db.newInterator();
            //noinspection TryFinallyCanBeTryWithResources
            try {
                iterator.seekToFirst(startWith.toByteArray());
                for (iterator.seekToFirst(startWith.toByteArray()); iterator.isValid(); iterator.next()) {
                    final ByteString key = ByteString.copyFrom(iterator.key());
                    if (!key.startsWith(prefix)) {
                        break;
                    }
                    if (arrayList.size() == batch) {
                        return new Iterator(arrayList, key);
                    }
                    arrayList.add(ByteString.copyFrom(iterator.value()));
                }
            } finally {
                iterator.close();
            }
            return new Iterator(arrayList, null);
        } catch (LevelDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public Iterator fetchKeys(@Nonnull final ByteString prefix, @Nullable final ByteString nextTokenOrNull, final int batch) {
        Preconditions.checkNotNull(prefix);
        Preconditions.checkArgument(batch >= 1);
        final int batchQuery = Math.min(batch, 1000);
        final ArrayList<ByteString> arrayList = new ArrayList<>(batchQuery);
        final ByteString startWith = nextTokenOrNull == null ? prefix : nextTokenOrNull;
        try {
            final LevelIterator iterator = db.newInterator();
            //noinspection TryFinallyCanBeTryWithResources
            try {
                iterator.seekToFirst(startWith.toByteArray());
                for (iterator.seekToFirst(startWith.toByteArray()); iterator.isValid(); iterator.next()) {
                    final ByteString key = ByteString.copyFrom(iterator.key());
                    if (!key.startsWith(prefix)) {
                        break;
                    }
                    if (arrayList.size() == batch) {
                        return new Iterator(arrayList, key);
                    }
                    arrayList.add(key);
                }
            } finally {
                iterator.close();
            }
            return new Iterator(arrayList, null);
        } catch (LevelDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        db.close();
    }

}