/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.tools;

import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.*;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import com.google.common.base.Charsets;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkState;

class Listener extends AbstractBlockChainListener {

    private final NetworkParameters PARAMS = MainNetParams.get();
    boolean once = true;
    TreeMap<Integer, StoredBlock> checkpoints;
    BlockStore store;
    long oneMonthAgo;

    Listener(TreeMap<Integer,StoredBlock> checkpoints, BlockStore store, long oneMonthAgo) {
        this.checkpoints = checkpoints;
        this.store = store;
        this.oneMonthAgo = oneMonthAgo;
    }

    @Override
    public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
        int height = block.getHeight();
        long time = block.getHeader().getTimeSeconds();
        if (height % PARAMS.getInterval() == 0 && time <= oneMonthAgo) {
            System.out.println(String.format("Checkpointing block %s at height %d",
                    block.getHeader().getHash(), block.getHeight()));
            checkpoints.put(height, block);
        } else if (time > oneMonthAgo && once) {
            try {
                System.out.println(String.format("======================================="));
                once = false;
                StoredBlock prev = block.getPrev(store);
                int c = 0;
                while (prev != null && c <= PARAMS.getInterval() * 2) {
                    System.out.println(String.format("Checkpointing block %s at height %d",
                            prev.getHeader().getHash(), prev.getHeight()));
                    StoredBlock oldBlock = checkpoints.put(prev.getHeight(), prev);
                    prev = prev.getPrev(store);
                    c += 1;
                }
                System.out.println(String.format("======================================="));
            } catch (BlockStoreException e) {
                e.printStackTrace();
            }
        }
    }
}

/**
 * Downloads and verifies a full chain from your local peer, emitting checkpoints at each difficulty transition period
 * to a file which is then signed with your key.
 */
public class BuildCheckpoints {

    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final File PLAIN_CHECKPOINTS_FILE = new File("checkpoints");
    private static final File TEXTUAL_CHECKPOINTS_FILE = new File("checkpoints.txt");

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();

        // Sorted map of block height to StoredBlock object.
        final TreeMap<Integer, StoredBlock> checkpoints = new TreeMap<Integer, StoredBlock>();

        // Configure bitcoinj to fetch only headers, not save them to disk, connect to a local fully synced/validated
        // node and to save block headers that are on interval boundaries, as long as they are <1 month old.
        final BlockStore store = new MemoryBlockStore(PARAMS);
        final BlockChain chain = new BlockChain(PARAMS, store);
        final PeerGroup peerGroup = new PeerGroup(PARAMS, chain);
        peerGroup.addAddress(InetAddress.getLocalHost());
        long now = new Date().getTime() / 1000;
        peerGroup.setFastCatchupTimeSecs(now);

        final long oneMonthAgo = now - (86400 * 7);

        chain.addListener(new Listener(checkpoints, store, oneMonthAgo), Threading.SAME_THREAD);

        peerGroup.startAsync();
        peerGroup.awaitRunning();
        peerGroup.downloadBlockChain();

        checkState(checkpoints.size() > 0);

        // Write checkpoint data out.
        writeBinaryCheckpoints(checkpoints, PLAIN_CHECKPOINTS_FILE);
        writeTextualCheckpoints(checkpoints, TEXTUAL_CHECKPOINTS_FILE);

        peerGroup.stopAsync();
        peerGroup.awaitTerminated();
        store.close();

        // Sanity check the created files.
        sanityCheck(PLAIN_CHECKPOINTS_FILE, checkpoints.size());
        sanityCheck(TEXTUAL_CHECKPOINTS_FILE, checkpoints.size());
    }

    private static void writeBinaryCheckpoints(TreeMap<Integer, StoredBlock> checkpoints, File file) throws Exception {
        final FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest);
        digestOutputStream.on(false);
        final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
        dataOutputStream.writeBytes("CHECKPOINTS 1");
        dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
        digestOutputStream.on(true);
        dataOutputStream.writeInt(checkpoints.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for (StoredBlock block : checkpoints.values()) {
            block.serializeCompact(buffer);
            dataOutputStream.write(buffer.array());
            buffer.position(0);
        }
        dataOutputStream.close();
        Sha256Hash checkpointsHash = new Sha256Hash(digest.digest());
        System.out.println("Hash of checkpoints data is " + checkpointsHash);
        digestOutputStream.close();
        fileOutputStream.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

    private static void writeTextualCheckpoints(TreeMap<Integer, StoredBlock> checkpoints, File file) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), Charsets.US_ASCII));
        writer.println("TXT CHECKPOINTS 1");
        writer.println("0"); // Number of signatures to read. Do this later.
        writer.println(checkpoints.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for (StoredBlock block : checkpoints.values()) {
            block.serializeCompact(buffer);
            writer.println(CheckpointManager.BASE64.encode(buffer.array()));
            buffer.position(0);
        }
        writer.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

    private static void sanityCheck(File file, int expectedSize) throws IOException {
        CheckpointManager manager = new CheckpointManager(PARAMS, new FileInputStream(file));
        checkState(manager.numCheckpoints() == expectedSize);

        if (PARAMS.getId().equals(NetworkParameters.ID_MAINNET)) {
            StoredBlock test = manager.getCheckpointBefore(1527987896);
	    System.out.println("Height------------------" + test.getHeight());
            checkState(test.getHeight() == 0);
            checkState(test.getHeader().getHashAsString()
                    .equals("00000007afe3f727654b0853bc3aaaa7f75afac7e84d7838806e09f097cff7f9"));
        } else if (PARAMS.getId().equals(NetworkParameters.ID_TESTNET)) {
            StoredBlock test = manager.getCheckpointBefore(1390500000); // Thu Jan 23 19:00:00 CET 2014
            checkState(test.getHeight() == 167328);
            checkState(test.getHeader().getHashAsString()
                    .equals("0000000000035ae7d5025c2538067fe7adb1cf5d5d9c31b024137d9090ed13a9"));
        }
    }
}
