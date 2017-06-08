package com.digitalvotingpass.blockchain;

import android.os.Environment;
import android.util.Log;

import com.digitalvotingpass.passportconnection.PassportConnection;
import com.digitalvotingpass.passportconnection.PassportTransactionFormatter;
import com.digitalvotingpass.utilities.MultiChainAddressGenerator;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Asset;
import org.bitcoinj.core.AssetBalance;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MultiChainParams;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.Arrays;

public class BlockChain {
    public static final String PEER_IP = "188.226.149.56";
    private static BlockChain instance;
    private WalletAppKit kit;
    private BlockchainCallBackListener listener;
    private boolean initialized = false;

    private InetAddress peeraddr;
    private long addressChecksum = 0xcc350cafL;
    private String[] version = {"00", "62", "8f", "ed"};
    final NetworkParameters params = MultiChainParams.get(
            "00d7fa1a62c5f1eadd434b9f7a8a657a42bd895f160511af6de2d2cd690319b8",
            "01000000000000000000000000000000000000000000000000000000000000000000000059c075b5dd26a328e185333ce1464b7279d476fbe901c38a003e694906e01c073b633559ffff0020ae0000000101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff1704ffff002001040f4d756c7469436861696e20766f7465ffffffff0200000000000000002f76a91474f585ec0e5f452a80af1e059b9d5079ec501d5588ac1473706b703731000000000000ffffffff3b633559750000000000000000131073706b6e0200040101000104726f6f74756a00000000",
            6799,
            Integer.parseInt(Arrays.toString(version).replaceAll(", |\\[|\\]", ""), 16),
            addressChecksum,
            0xf5dec1feL
    );
    private Address masterAddress = Address.fromBase58(params, "1GoqgbPZUV2yuPZXohtAvB2NZbjcew8Rk93mMn");

    private BlockChain() {
        try {
            peeraddr = InetAddress.getByName(PEER_IP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public static synchronized BlockChain getInstance() {
        if (instance == null) {
            instance = new BlockChain();
        }
        return instance;
    }

    public void setCallBackListener(BlockchainCallBackListener listener) {
        this.listener = listener;
        if (kit != null && listener != null)
            kit = kit.setDownloadListener(new ProgressTracker(listener));
    }

    public void startDownload() {
        if (!initialized) {
            BriefLogFormatter.init();
            String filePrefix = "voting-wallet";
            File walletFile = new File(Environment.getExternalStorageDirectory() + "/DigitalVotingPass");
            if (!walletFile.exists()) {
                walletFile.mkdirs();
            }
            kit = new WalletAppKit(params, walletFile, filePrefix);

            if (listener != null)
                kit = kit.setDownloadListener(new ProgressTracker(listener));
            kit.setBlockingStartup(false);

            PeerAddress peer = new PeerAddress(params, peeraddr);
            kit.setPeerNodes(peer);
            kit.startAsync();
        }
    }

    public Service.State state() {
        if (kit == null)
            return null;
        return kit.state();
    }

    public void disconnect() {
        kit.stopAsync();
    }

    /**
     * Gets the amount of voting tokens associated with the given public key.
     * @param pubKey - The Public Key read from the ID of the voter
     * @param mcAsset - The asset (election) that is chosen at app start-up.
     * @return - The amount of voting tokens available
     * @throws NullPointerException - When the address is not found on the blockchain.
     */
    public int getVotingPassAmount(PublicKey pubKey, Asset mcAsset) {
        Log.e(this.toString(), "total assets: " + kit.wallet().getAvailableAssets().size());
        Address mcAddress = Address.fromBase58(params, MultiChainAddressGenerator.getPublicAddress(version, Long.toString(addressChecksum), pubKey));
        mcAsset = kit.wallet().getAvailableAssets().get(0); // TODO: remove this
        Log.e(this.toString(), "asset 0: " + kit.wallet().getAssetBalance(mcAsset, mcAddress).getBalance());
        return (int) kit.wallet().getAssetBalance(mcAsset, mcAddress).getBalance();
    }

    /**
     * Get the balance of a public key based on the information on the blockchain.
     * @param pubKey
     * @return
     */
    public AssetBalance getVotingPassBalance(PublicKey pubKey, Asset asset) {
        Address address = Address.fromBase58(params, MultiChainAddressGenerator.getPublicAddress(version, Long.toString(addressChecksum), pubKey));
        return kit.wallet().getAssetBalance(asset, address);
    }

    /**
     * Spends all outputs in this balance to the master address.
     * @param balance
     * @param pcon
     */
    public void confirmVotingPass(AssetBalance balance, PassportConnection pcon) {
        for (TransactionOutput utxo : balance) {
            this.spendUtxo(utxo, this.masterAddress, pcon);
        }
    }

    /**
     * Create a new transaction, signes it with the travel document and broadcasts it to the
     * network.
     * @param utxo
     * @param destination
     * @param pcon
     */
    public void spendUtxo(TransactionOutput utxo, Address destination, PassportConnection pcon) {

        Transaction tx = new PassportTransactionFormatter(utxo, destination)
                .buildAndSign(pcon)
                .getTransaction(params);

        final Wallet.SendResult result = new Wallet.SendResult();
        result.tx = tx;
        result.broadcast = kit.peerGroup().broadcastTransaction(tx);
        result.broadcastComplete = result.broadcast.future();

        result.broadcastComplete.addListener(new Runnable() {
            @Override
            public void run() {
                System.out.println("Asset spent! txid: " + result.tx.getHashAsString());
            }
        }, MoreExecutors.directExecutor());
    }
}
