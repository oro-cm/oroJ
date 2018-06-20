/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 The Oro developers
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

package org.bitcoinj.params;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.Block.BLOCK_VERSION_0_3;
import static org.bitcoinj.core.Coin.COIN;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends NetworkParameters {

    public MainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1e00ffffL);
        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        port = 9777;
        packetMagic = 0xf9beb4e0L;
        id = ID_MAINNET;
        spendableCoinbaseDepth = 100;
        genesisReward = COIN.multiply(0);
        liveFeedSwitchTime = 1527746400;
        powSwitchHeight = 0;
        liveFeedUrl = "http://oro.org/";

        // A script containing the difficulty bits and the following message:
        //
        //   "Oro genesis / MainNet"
        CharSequence inputScriptHex = "0004ffff001e0104194f726f436f696e2067656e65736973202f204d61696e4e6574";
        genesisBlock = createGenesis(this, inputScriptHex);
        genesisBlock.setVersion(BLOCK_VERSION_0_3);
        genesisBlock.setDifficultyTarget(0x1e00ffffL);
        genesisBlock.setTime(1527746400L);
        genesisBlock.setNonce(160817071);
        String genesisHash = genesisBlock.getHashAsString();

        checkState(genesisBlock.getMerkleRoot().toString().equals("a63c3a572fcd0467afce40768413df46d7854859d091749d46c53fd82b66f6cd"));
        checkState(genesisHash.equals("00000007afe3f727654b0853bc3aaaa7f75afac7e84d7838806e09f097cff7f9"),
                   genesisHash);

        alertSigningKey = Utils.HEX.decode("0426d1c5aac0e7b98f37f5f8ca10a18bb915820516723a727093cca65108ac24cdf3467ff06a39ad388ccc3d83802c85df73dba14e3db3835cec6892b9647e92fa");

        checkpoints.put(0, new Sha256Hash("00000007afe3f727654b0853bc3aaaa7f75afac7e84d7838806e09f097cff7f9"));
//	checkpoints.put(119000, new Sha256Hash("b751c902626f57b9536dc793b6fa048aa5061b89b1cdcea65c172bbbf11154bf"));
        dnsSeeds = new String[] {
                "main.seeds.dynamiccoin.net"
        };
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
