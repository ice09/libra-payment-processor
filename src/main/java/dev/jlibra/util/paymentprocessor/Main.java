package dev.jlibra.util.paymentprocessor;

import dev.jlibra.JLibra;
import dev.jlibra.KeyUtils;
import dev.jlibra.admissioncontrol.query.SignedTransactionWithProof;
import dev.jlibra.admissioncontrol.query.UpdateToLatestLedgerResult;
import dev.jlibra.spring.action.AccountStateQuery;
import dev.jlibra.spring.action.PeerToPeerTransfer;
import dev.jlibra.util.JLibraUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@ComponentScan(basePackages = {"dev.jlibra"})
public class Main implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final PrivateKey SENDER_PRIVATE_KEY = KeyUtils.privateKeyFromHexString(
            "3051020101300506032b657004220420a8b276d534eb9b6f9c7b2dea33b9623082fd28b903cd433feb538171a6ff184381210078f6c7efa2cf6a54eeabe819ddec1e44d6a7d5a68f8780f614e5c35ef9f1956b");
    private final PublicKey SENDER_PUBLIC_KEY = KeyUtils.publicKeyFromHexString(
            "302a300506032b657003210078f6c7efa2cf6a54eeabe819ddec1e44d6a7d5a68f8780f614e5c35ef9f1956b");

    private final int MAX_RETRIES = 3;

    @Autowired
    private PeerToPeerTransfer peerToPeerTransfer;

    @Autowired
    private AccountStateQuery accountStateQuery;

    @Autowired
    private ExcelReader excelReader;

    @Autowired
    private JLibra jLibra;

    @Autowired
    private JLibraUtil jLibraUtil;

    public static void main(String[] args) {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        SpringApplication.run(Main.class, args);
    }

    public void run(String... args) throws Exception {
        verifyBlockchainConnection();
        processPaymentsFromExcelsheet();
    }

    private void verifyBlockchainConnection() {
        log.info("Configured Admission Control " + jLibra.getAdmissionControl());
    }

    private long transferLibraToReceiver(String receiverAddress, BigDecimal amount) {
        long seqNo = jLibraUtil.maybeFindSequenceNumber(KeyUtils.toHexStringLibraAddress(SENDER_PUBLIC_KEY.getEncoded()));
        PeerToPeerTransfer.PeerToPeerTransferReceipt receipt =
                peerToPeerTransfer.transferFunds(receiverAddress, amount.longValue() * 1_000_000, SENDER_PUBLIC_KEY, SENDER_PRIVATE_KEY, jLibra.getGasUnitPrice(), jLibra.getMaxGasAmount());
        System.out.println(" (seqNo " + seqNo + ") " + receipt.getStatus());
        return seqNo;
    }

    private void processPaymentsFromExcelsheet() throws Exception {
        ExcelReader excelReader = new ExcelReader();
        List<Map<String, String>> excelData = excelReader.readExcel();
        for (Map<String, String> rowData : excelData) {
            String recvAddr = null;
            BigDecimal amount = BigDecimal.ZERO;
            for (String key : rowData.keySet()) {
                switch (key) {
                    case "Libra Address": recvAddr = rowData.get(key); break;
                    case "Amount": amount = new BigDecimal(rowData.get(key)); break;
                    case "Signature": break; // TODO verify signature
                }
            }
            System.out.print("Sending " + amount + " Libra to " + recvAddr);
            long seqNo = transferLibraToReceiver(recvAddr, amount);
            for (int tries = 0; tries <= MAX_RETRIES; tries++) {
                if (fetchEventsForSequenceNumber(KeyUtils.toByteArrayLibraAddress(SENDER_PUBLIC_KEY.getEncoded()), seqNo) == 0) {
                    if (tries == MAX_RETRIES) {
                        System.out.println("    maximum retries reached. Marking transaction for manual verification.");
                    }
                } else {
                    break;
                }
            }
        }
    }

    private int fetchEventsForSequenceNumber(byte[] address, long seqNo) {
        UpdateToLatestLedgerResult result = accountStateQuery.queryTransactionsBySequenceNumber(address, seqNo);
        Optional<SignedTransactionWithProof> trxBySeqNo = result.getAccountTransactionsBySequenceNumber().stream().findFirst();
        return trxBySeqNo.isPresent() ? trxBySeqNo.get().getEvents().size() : 0;
    }

}
