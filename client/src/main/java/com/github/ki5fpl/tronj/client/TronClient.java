package com.github.ki5fpl.tronj.client;

import com.github.ki5fpl.tronj.crypto.SECP256K1;
import com.github.ki5fpl.tronj.utils.Base58Check;
import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI.Return;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.api.WalletGrpc;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.contract.BalanceContract.TransferContract;

public class TronClient {
    public final WalletGrpc.WalletBlockingStub blockingStub;
    public final SECP256K1.KeyPair keyPair;

    public TronClient(String grpcEndpoint, String hexPrivateKey) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(grpcEndpoint).usePlaintext().build();
        blockingStub = WalletGrpc.newBlockingStub(channel);
        keyPair = SECP256K1.KeyPair.create(SECP256K1.PrivateKey.create(Bytes32.fromHexString(hexPrivateKey)));
    }

    public TronClient(Channel channel, String hexPrivateKey) {
        blockingStub = WalletGrpc.newBlockingStub(channel);
        keyPair = SECP256K1.KeyPair.create(SECP256K1.PrivateKey.create(Bytes32.fromHexString(hexPrivateKey)));
    }

    public static TronClient ofMainnet(String hexPrivateKey) {
        return new TronClient("grpc.trongrid.io:50051", hexPrivateKey);
    }

    public static TronClient ofShasta(String hexPrivateKey) {
        return new TronClient("grpc.shasta.trongrid.io:50051", hexPrivateKey);
    }

    public static TronClient ofNile(String hexPrivateKey) {
        return new TronClient("47.252.19.181:50051", hexPrivateKey);
    }

    public static String generateAddress() {
        // generate random address
        SECP256K1.KeyPair kp = SECP256K1.KeyPair.generate();

        SECP256K1.PublicKey pubKey = kp.getPublicKey();
        Keccak.Digest256 digest = new Keccak.Digest256();
        digest.update(pubKey.getEncoded(), 0, 64);
        byte[] raw = digest.digest();
        byte[] rawAddr = new byte[21];
        rawAddr[0] = 0x41;
        System.arraycopy(raw, 12, rawAddr, 1, 20);

        System.out.println("Base58Check: " + Base58Check.bytesToBase58(rawAddr));
        System.out.println("Hex Address: " + Hex.toHexString(rawAddr));
        System.out.println("Public Key:  " + Hex.toHexString(pubKey.getEncoded()));
        System.out.println("Private Key: " + Hex.toHexString(kp.getPrivateKey().getEncoded()));

        return Hex.toHexString(kp.getPrivateKey().getEncoded());
    }

    public Transaction signTransaction(TransactionExtention txnExt) {
        SECP256K1.Signature sig = SECP256K1.sign(Bytes32.wrap(txnExt.getTxid().toByteArray()), keyPair);
        Transaction signedTxn =
            txnExt.getTransaction().toBuilder().addSignature(ByteString.copyFrom(sig.encodedBytes().toArray())).build();
        return signedTxn;
    }

    public Transaction signTransaction(Transaction txn) {
        SHA256.Digest digest = new SHA256.Digest();
        digest.update(txn.getRawData().toByteArray());
        byte[] txid = digest.digest();
        SECP256K1.Signature sig = SECP256K1.sign(Bytes32.wrap(txid), keyPair);
        Transaction signedTxn = txn.toBuilder().addSignature(ByteString.copyFrom(sig.encodedBytes().toArray())).build();
        return signedTxn;
    }

    public void transfer(String from, String to, long amount) throws Exception {
        System.out.println("Transfer from: " + from);
        System.out.println("Transfer to: " + from);

        byte[] rawFrom = Base58Check.base58ToBytes(from);
        byte[] rawTo = Base58Check.base58ToBytes(to);

        TransferContract req = TransferContract.newBuilder()
                                   .setOwnerAddress(ByteString.copyFrom(rawFrom))
                                   .setToAddress(ByteString.copyFrom(rawTo))
                                   .setAmount(amount)
                                   .build();
        System.out.println("transfer => " + req.toString());

        TransactionExtention txnExt = blockingStub.createTransaction2(req);
        System.out.println("txn id => " + Hex.toHexString(txnExt.getTxid().toByteArray()));

        Transaction signedTxn = signTransaction(txnExt);

        System.out.println(signedTxn.toString());
        Return ret = blockingStub.broadcastTransaction(signedTxn);
        System.out.println("======== Result ========\n" + ret.toString());
    }
}
