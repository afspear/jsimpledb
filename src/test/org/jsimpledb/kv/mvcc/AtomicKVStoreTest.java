
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.kv.mvcc;

import com.google.common.base.Converter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.jsimpledb.TestSupport;
import org.jsimpledb.kv.CloseableKVStore;
import org.jsimpledb.kv.KVPair;
import org.jsimpledb.kv.KVStore;
import org.jsimpledb.kv.KeyRange;
import org.jsimpledb.kv.bdb.BerkeleyKVDatabase;
import org.jsimpledb.kv.leveldb.LevelDBAtomicKVStore;
import org.jsimpledb.kv.simple.SimpleKVDatabase;
import org.jsimpledb.kv.simple.XMLKVDatabase;
import org.jsimpledb.kv.util.NavigableMapKVStore;
import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.ConvertedNavigableMap;
import org.jsimpledb.util.LongEncoder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AtomicKVStoreTest extends TestSupport {

    private File dir;

    @AfterClass
    public void cleanup() throws IOException {
        Files.walkFileTree(this.dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @DataProvider(name = "kvstores")
    public AtomicKVStore[][] genAtomicKVStores() throws IOException {

        // Create directory
        this.dir = File.createTempFile(this.getClass().getSimpleName(), null);
        if (!this.dir.delete() || !this.dir.mkdirs())
            throw new IOException("can't create " + dir);
        final ArrayList<AtomicKVStore> list = new ArrayList<>();

        // Memory
        list.add(new AtomicKVDatabase(new SimpleKVDatabase(new NavigableMapKVStore(), 250, 500)));

        // XMLDB
        final File xmlFile = new File(this.dir, "xmldb.xml");
        list.add(new AtomicKVDatabase(new XMLKVDatabase(xmlFile)));

        // BerkeleyDB
        final BerkeleyKVDatabase bdb = new BerkeleyKVDatabase();
        final File bdbDir = new File(this.dir, "bdb");
        bdbDir.mkdirs();
        bdb.setDirectory(bdbDir);
        list.add(new AtomicKVDatabase(bdb));

        // LevelDB
        final LevelDBAtomicKVStore leveldb = new LevelDBAtomicKVStore();
        leveldb.setDirectory(new File(this.dir, "leveldb"));
        leveldb.setCreateIfMissing(true);
        list.add(leveldb);

        // Done
        final AtomicKVStore[][] array = new AtomicKVStore[list.size()][];
        for (int i = 0; i < array.length; i++)
            array[i] = new AtomicKVStore[] { list.get(i) };
        return array;
    }

    @Test(dataProvider = "kvstores")
    public void testAtomicKVStore(AtomicKVStore kv) throws Exception {

        // Start kvstore
        kv.start();

        // Test
        final TreeMap<byte[], byte[]> map = new TreeMap<byte[], byte[]>(ByteUtil.COMPARATOR);
        for (int count = 0; count < 200; count++) {
            this.log.trace("[" + count + "] next iteration");
            Writes writes;

            // Do puts atomically
            writes = this.getPuts(count, map);
            kv.mutate(writes, true);
            this.compare(this.read(count, kv), map);
            Thread.sleep(5);

            // Do removes non-atomically
            writes = this.getRemoves(count, map);
            writes.applyTo(kv);
            this.compare(this.read(count, kv), map);
            Thread.sleep(5);

            // Do puts non-atomically
            writes = this.getPuts(count, map);
            writes.applyTo(kv);
            this.compare(this.read(count, kv), map);
            Thread.sleep(5);

            // Do removes atomically
            writes = this.getRemoves(count, map);
            kv.mutate(writes, true);
            this.compare(this.read(count, kv), map);
            Thread.sleep(5);
        }

        // Stop kvstore
        kv.stop();
    }

    private Writes getPuts(int count, TreeMap<byte[], byte[]> map) {
        final Writes writes = new Writes();
        for (int i = 0; i < 16; i++) {
            final byte[] key = new byte[] { (byte)this.random.nextInt(0xff) };
            final byte[] key2 = new byte[] { (byte)this.random.nextInt(0xff), (byte)this.random.nextInt(0xff) };
            final byte[] value = LongEncoder.encode((1 << i) + i);
            this.log.trace("[" + count + "]: PUT: " + ByteUtil.toString(key) + " -> " + ByteUtil.toString(value));
            writes.getPuts().put(key, value);
            this.log.trace("[" + count + "]: PUT: " + ByteUtil.toString(key2) + " -> " + ByteUtil.toString(value));
            writes.getPuts().put(key2, value);
            map.put(key, value);
            map.put(key2, value);
        }
        return writes;
    }

    private Writes getRemoves(int count, TreeMap<byte[], byte[]> map) {
        final Writes writes = new Writes();
        for (int i = 0; i < 9; i++) {
            if (this.random.nextInt(5) > 0) {
                final byte[] key = new byte[] { (byte)this.random.nextInt(0xff) };
                this.log.trace("[" + count + "]: REMOVE: " + ByteUtil.toString(key));
                writes.setRemoves(writes.getRemoves().add(new KeyRange(key)));
                map.remove(key);
            } else {
                final byte[] x = this.random.nextInt(10) == 0 ? new byte[0] : new byte[] { (byte)this.random.nextInt(0xff) };
                final byte[] y = this.random.nextInt(10) == 0 ? null : new byte[] { (byte)this.random.nextInt(0xff) };
                final byte[] minKey = y == null || ByteUtil.compare(x, y) < 0 ? x : y;
                final byte[] maxKey = y == null || ByteUtil.compare(x, y) < 0 ? y : x;
                this.log.trace("[" + count + "]: REMOVE: [" + ByteUtil.toString(minKey) + ", " + ByteUtil.toString(maxKey) + ")");
                writes.setRemoves(writes.getRemoves().add(new KeyRange(minKey, maxKey)));
                if (maxKey == null)
                    map.tailMap(minKey, true).clear();
                else
                    map.subMap(minKey, true, maxKey, false).clear();
            }
        }
        return writes;
    }

    private TreeMap<byte[], byte[]> read(int count, AtomicKVStore kv) {
        return this.read(count, kv, ByteUtil.EMPTY, null);
    }

    private TreeMap<byte[], byte[]> read(int count, AtomicKVStore lkv, byte[] minKey, byte[] maxKey) {
        final TreeMap<byte[], byte[]> map = new TreeMap<byte[], byte[]>(ByteUtil.COMPARATOR);
        this.log.trace("[" + count + "]: reading kv store");
        final KVStore kv;
        final CloseableKVStore snapshot;
        if (this.random.nextBoolean()) {
            snapshot = lkv.snapshot();
            kv = snapshot;
        } else {
            snapshot = null;
            kv = lkv;
        }
        try {
            for (Iterator<KVPair> i = kv.getRange(minKey, maxKey, false); i.hasNext(); ) {
                final KVPair pair = i.next();
                map.put(pair.getKey(), pair.getValue());
            }
        } finally {
            if (snapshot != null)
                snapshot.close();
        }
        return map;
    }

    private void compare(TreeMap<byte[], byte[]> map1, TreeMap<byte[], byte[]> map2) {
        final NavigableMap<String, String> smap1 = this.stringView(map1);
        final NavigableMap<String, String> smap2 = this.stringView(map2);
        Assert.assertEquals(smap1, smap2, "\n*** ACTUAL:\n" + smap1 + "\n*** EXPECTED:\n" + smap2 + "\n");
    }

    private NavigableMap<String, String> stringView(NavigableMap<byte[], byte[]> byteMap) {
        if (byteMap == null)
            return null;
        final Converter<String, byte[]> converter = ByteUtil.STRING_CONVERTER.reverse();
        return new ConvertedNavigableMap<String, String, byte[], byte[]>(byteMap, converter, converter);
    }
}

