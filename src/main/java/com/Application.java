package com;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.ChangeTrustOperation;
import org.stellar.sdk.CreateAccountOperation;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.ManageOfferOperation;
import org.stellar.sdk.Network;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.Server;
import org.stellar.sdk.SetOptionsOperation;
import org.stellar.sdk.requests.OffersRequestBuilder;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.OfferResponse;
import org.stellar.sdk.responses.Page;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

public class Application {
    private static boolean stop = false;

    public static void main(String[] args) throws IOException {
        Network.useTestNetwork();
        Server server = new Server("https://horizon-testnet.stellar.org");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (!stop) {
            try {
                menu(reader, server);
            } catch (Exception e) {
                System.out.println("Error!");
                System.out.println(e.getMessage());
            }
        }
    }

    private static void menu(BufferedReader reader, Server server) throws Exception {
        System.out.println("\nClick: 1 - create ACCOUNT Friendbot, 2 - create ACCOUNT Another Account, 3 - print account INFO," +
                " 4 - change TRUST,\n 5 - send ASSET, 6 - , 7 - SSC crowdfunding, 8 - issuing Custom Assets, " +
                "9 - create OFFER, 0 - exit");
        int number = Integer.parseInt(reader.readLine());
        switch (number) {
            case 1:
                createAccountFriendbot(server);
                break;
            case 2:
                System.out.println("Enter secret seed of SOURCE account:");
                String secretSeedSourceAccount = reader.readLine();
                createAccountAnotherAccount(secretSeedSourceAccount, reader, server);
                break;
            case 3:
                System.out.println("Enter secret seed of account:");
                String secretSeed = reader.readLine();
                printAccountInfo(secretSeed, server);
                break;
            case 4:
                changeTrust(reader, server);
                break;
            case 5:
                sendAsset(reader, server);
                break;
            case 6:
                break;
            case 7:
                crowdfundingSSC(reader, server);
                break;
            case 8:
                issuingCustomAssets(reader, server);
                break;
            case 9:
                createOffer(reader, server);
                break;
            case 0:
                reader.close();
                stop = true;
                break;
            default:
                System.out.println("Input Error!");
                break;
        }
    }

    private static void changeTrust(BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * CHANGE TRUST * * * ");
        Asset asset = assetCreation(reader);

        //Receiving account (prepare).
        System.out.println("Enter secret seed of RECEIVING account:");
        KeyPair receivingKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse receivingAccount = server.accounts().account(receivingKeys);

        System.out.println("Receiving account: ");
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);

        //Trust transaction creation.
        System.out.println("Enter trust limit:");
        String trustLimit = reader.readLine();

        Transaction trustTransaction = new Transaction.Builder(receivingAccount)
                .addOperation(new ChangeTrustOperation.Builder(asset, trustLimit).build())
                .build();
        trustTransaction.sign(receivingKeys);
        System.out.println("Trust transaction: " + server.submitTransaction(trustTransaction).isSuccess());

        //Receiving account (check changes).
        System.out.println("Receiving account: ");
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);
    }

    private static KeyPair createHoldingAccount(Server server, AccountResponse accountA, KeyPair keyPairA) throws IOException {
        System.out.println("\n * * * Transaction 1: Create the Holding Account * * * Account A SN: " + accountA.getSequenceNumber());
        KeyPair keyPair = KeyPair.random();
        Transaction createHoldingAccount = new Transaction.Builder(accountA)
                .addOperation(new CreateAccountOperation.Builder(keyPair, "500").build())
                .build();
        createHoldingAccount.sign(keyPairA);
        System.out.println("SUCCESS: " + server.submitTransaction(createHoldingAccount).isSuccess());
        printAccountInfo(new String(keyPair.getSecretSeed()), server);
        System.out.println("SN - M: " + createHoldingAccount.getSequenceNumber());
        return keyPair;
    }

    private static void addSigners(Server server, AccountResponse accountH, KeyPair keyPairH, KeyPair keyPairA, KeyPair keyPairB) throws IOException {
        System.out.println("\n * * * Transaction 2: Add signers * * * Account H SN: " + accountH.getSequenceNumber());
        Transaction transactionSigners = new Transaction.Builder(accountH)
                .addOperation(new SetOptionsOperation.Builder()
                        .setSigner(keyPairA.getXdrSignerKey(), 1).build())
                .addOperation(new SetOptionsOperation.Builder()
                        .setSigner(keyPairB.getXdrSignerKey(), 1).build())
                .addOperation(new SetOptionsOperation.Builder()
                        .setMasterKeyWeight(0)
                        .setLowThreshold(2)
                        .setMediumThreshold(2)
                        .setHighThreshold(2)
                        .build())
                .build();
        transactionSigners.sign(keyPairH);
        System.out.println("SUCCESS signer: " + server.submitTransaction(transactionSigners).isSuccess());
        System.out.println("SN - N: " + transactionSigners.getSequenceNumber());
    }

    private static Asset issuingAsset(String assetName, Server server, KeyPair keyPairA, KeyPair keyPairB, KeyPair keyPairH, String amountV) throws IOException {
        //??After Transaction 2, the holding account should be funded with the tokens to be used for the crowdfunding
        //campaign, as well as with enough lumens to cover the transaction fees for all of the following transactions.
        System.out.println("\n * * * Transaction 2+ Holding account should be funded with the tokens * * * ");
        AccountResponse accountA = server.accounts().account(keyPairA);
        AccountResponse accountB = server.accounts().account(keyPairB);
        AccountResponse accountH = server.accounts().account(keyPairH);

        Asset asset = Asset.createNonNativeAsset(assetName, keyPairA);
        System.out.println("Asset created: " + assetName);

        Transaction trustTransaction = new Transaction.Builder(accountH)
                .addOperation(new ChangeTrustOperation.Builder(asset, amountV).build())
                .build();
        trustTransaction.sign(keyPairA);
        trustTransaction.sign(keyPairB);
        System.out.println("SUCCESS trust: " + server.submitTransaction(trustTransaction).isSuccess() +
                "; SN - ?: " + trustTransaction.getSequenceNumber());
        printAccountInfo(new String(keyPairH.getSecretSeed()), server);

        Transaction issuingAsset = new Transaction.Builder(accountA)
                .addOperation(new PaymentOperation.Builder(keyPairH, asset, amountV).build())
                .build();
        issuingAsset.sign(keyPairA);
        System.out.println("SUCCESS payment: " + server.submitTransaction(issuingAsset).isSuccess() +
                "; SN - ?: " + issuingAsset.getSequenceNumber());
        printAccountInfo(new String(keyPairH.getSecretSeed()), server);
        return asset;
    }

    private static void offerSell(Server server, KeyPair keyPairH, KeyPair keyPairA, KeyPair keyPairB, Asset asset, String amountV) throws IOException, InterruptedException {
        System.out.println("\n * * * Transaction 3: Begin Crowdfunding - sell offer * * * \n" +
                "sleep 3 sec");
        Thread.sleep(3000);
        AccountResponse accountH = server.accounts().account(keyPairH);
        System.out.println("Account H SN: " + accountH.getSequenceNumber());
        ManageOfferOperation offerOperation = new ManageOfferOperation.Builder(asset, new AssetTypeNative(), amountV, "1")
                .setOfferId(0)
                .build();
        Transaction offerTransaction = new Transaction.Builder(accountH)
                .addOperation(offerOperation)
                .build();
        offerTransaction.sign(keyPairA);
        offerTransaction.sign(keyPairB);
        System.out.println("SUCCESS sell offer: " + server.submitTransaction(offerTransaction).isSuccess());
        printAccountInfo(new String(keyPairH.getSecretSeed()), server);
        System.out.println("SN - N+1: " + offerTransaction.getSequenceNumber());
    }

    private static void sendInvestment(Server server, Asset asset, KeyPair keyPairA, KeyPair keyPairB, String amountA, String amountB) throws IOException {
        AccountResponse accountA = server.accounts().account(keyPairA);
        AccountResponse accountB = server.accounts().account(keyPairB);

        System.out.println(" * * * Send investment A");
        Transaction sendInvestmentA = new Transaction.Builder(accountA)
                .addOperation(new ManageOfferOperation.Builder(new AssetTypeNative(), asset, amountA, "1")
                        .setOfferId(0)
                        .build())
                .build();
        sendInvestmentA.sign(keyPairA);
        System.out.println("Send investment A submit is SUCCESS: " + server.submitTransaction(sendInvestmentA).isSuccess());
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);

        System.out.println(" * * * Trust transaction B");
        Transaction trustTransactionB = new Transaction.Builder(accountB)
                .addOperation(new ChangeTrustOperation.Builder(asset, amountB).build())
                .build();
        trustTransactionB.sign(keyPairB);
        System.out.println("Trust transaction B submit is SUCCESS: " + server.submitTransaction(trustTransactionB).isSuccess());
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);

        System.out.println(" * * * Send investment B");
        Transaction sendInvestmentB = new Transaction.Builder(accountB)
                .addOperation(new ManageOfferOperation.Builder(new AssetTypeNative(), asset, amountB, "1")
                        .setOfferId(0)
                        .build())
                .build();
        sendInvestmentB.sign(keyPairB);
        System.out.println("Send investment B submit is SUCCESS: " + server.submitTransaction(sendInvestmentB).isSuccess());
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);
    }

    private static Transaction paymentToTarget(Server server, KeyPair keyPairH, KeyPair keyPairA, KeyPair keyPairB, KeyPair keyPairT, String amountV) throws IOException {
        AccountResponse accountH = server.accounts().account(keyPairH);
        System.out.println("\n * * * T 4 (sign) * * * H SN - " + accountH.getSequenceNumber());
        Transaction sendAssetV = new Transaction.Builder(accountH)
                .addOperation(new PaymentOperation.Builder(keyPairT, new AssetTypeNative(), amountV).build())
                //.addTimeBounds(new TimeBounds(10, 120))
                .build();
        sendAssetV.sign(keyPairA);
        sendAssetV.sign(keyPairB);
        return sendAssetV;
    }

    private static boolean allAssetsSell(Server server, KeyPair keyPair, String assetName) throws IOException {
        System.out.println(" * * * allAssetsSell * * * ");
        AccountResponse account = server.accounts().account(keyPair);
        for (AccountResponse.Balance balance : account.getBalances()) {
            String code = balance.getAssetCode();
            String bal = balance.getBalance();
            if (code.equals(assetName)) {
                if (bal.equals("0.0000000")) {
                    return true;
                } else {
                    break;
                }
            }
        }
        return false;
    }

    private static Transaction offerCancelAndReturn(BufferedReader reader, Server server, Asset asset, KeyPair keyPairH, KeyPair keyPairA, KeyPair keyPairB, String amountA, String amountB) throws IOException {
        AccountResponse accountH = server.accounts().account(keyPairH);
        System.out.println("\n * * * T 5 (sign): offerCancel & offerReturn * * * H SN: " + accountH.getSequenceNumber());
        System.out.println(" * * * Enter offer operation id to cancel:");
        printOffersId(new String(keyPairH.getSecretSeed()), server);
        ManageOfferOperation offerCancelOperation = new ManageOfferOperation.Builder(asset, new AssetTypeNative(), "0", "1")
                .setOfferId(Long.parseLong(reader.readLine()))
                .build();
        String returnAmount = Integer.parseInt(amountA) + Integer.parseInt(amountB) + "";
        ManageOfferOperation offerReturnOperation = new ManageOfferOperation.Builder(new AssetTypeNative(), asset, returnAmount, "1")
                .setOfferId(0)
                .build();
        Transaction offerCancelAndReturn = new Transaction.Builder(accountH)
                .addOperation(offerCancelOperation)
                .addOperation(offerReturnOperation)
                //.addTimeBounds(new TimeBounds(10, 120))
                .build();
        offerCancelAndReturn.sign(keyPairA);
        offerCancelAndReturn.sign(keyPairB);
        return offerCancelAndReturn;
    }
    private static void returnInvestments(Server server, Asset asset, KeyPair keyPairA, KeyPair keyPairB, String amountA, String amountB) throws IOException {
        //returnInvestmentA
        AccountResponse accountA = server.accounts().account(keyPairA);
        AccountResponse accountB = server.accounts().account(keyPairB);
        Transaction returnInvestmentA = new Transaction.Builder(accountA)
                .addOperation(new ManageOfferOperation.Builder(asset, new AssetTypeNative(), amountA, "1")
                        .setOfferId(0)
                        .build())
                .build();
        returnInvestmentA.sign(keyPairA);
        System.out.println("Offer return investment A submit is SUCCESS: " + server.submitTransaction(returnInvestmentA).isSuccess() +
                "; SequenceNumber: " + returnInvestmentA.getSequenceNumber());

        //returnInvestmentB
        Transaction returnInvestmentB = new Transaction.Builder(accountB)
                .addOperation(new ManageOfferOperation.Builder(asset, new AssetTypeNative(), amountB, "1")
                        .setOfferId(0)
                        .build())
                .build();
        returnInvestmentB.sign(keyPairB);
        System.out.println("Offer return investment B submit is SUCCESS: " + server.submitTransaction(returnInvestmentB).isSuccess() +
                "; SequenceNumber: " + returnInvestmentB.getSequenceNumber());

    }

    private static void crowdfundingSSC(BufferedReader reader, Server server) throws IOException, InterruptedException {
        System.out.println(" * * * CROWDFUNDING SSC * * * ");
        String amountV = "10";
        String amountA = "5";
        String amountB = "4";

        System.out.println("Enter secret seed of party A account:");
        KeyPair keyPairA = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse accountA = server.accounts().account(keyPairA);
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);

        System.out.println("Enter secret seed of party B account:");
        KeyPair keyPairB = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse accountB = server.accounts().account(keyPairB);
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);

        System.out.println("Enter secret seed of TARGET account:");
        KeyPair keyPairT = KeyPair.fromSecretSeed(reader.readLine());
        printAccountInfo(new String(keyPairT.getSecretSeed()), server);

        //T1
        KeyPair keyPairH = createHoldingAccount(server, accountA, keyPairA);
        AccountResponse accountH = server.accounts().account(keyPairH);

        //T2
        addSigners(server, accountH, keyPairH, keyPairA, keyPairB);

        //T2+
        System.out.println("Enter asset NAME: ");
        String assetName = reader.readLine();
        Asset asset = issuingAsset(assetName, server, keyPairA, keyPairB, keyPairH, amountV);


        //T3 - sell offer
        offerSell(server, keyPairH, keyPairA, keyPairB, asset, amountV);

        //T4(sign) Send V to Target.
        Transaction sendAssetV = paymentToTarget(server, keyPairH, keyPairA, keyPairB, keyPairT, amountV);

        //T5(sign) offerCancelAndReturn
        Transaction offerCancelAndReturn = offerCancelAndReturn(reader, server, asset, keyPairH, keyPairA, keyPairB, amountA, amountB);

        //Investment
        sendInvestment(server, asset, keyPairA, keyPairB, amountA, amountB);

        //Finish
        //T4(submit) Send V to Target.
        if (allAssetsSell(server, keyPairH, assetName)) {
            System.out.println("\n * * * Crowdfunding SUCCESS * * * ");
            System.out.println("SUCCESS V to TARGET: " + server.submitTransaction(sendAssetV).isSuccess());
            System.out.println("T 4: SN - N+2: " + sendAssetV.getSequenceNumber());
        } else {
            System.out.println("\n * * * Crowdfunding FAILS * * * ");
            System.out.println("SUCCESS offer cancel and return: " + server.submitTransaction(offerCancelAndReturn).isSuccess() +
                    "; T 5: SN - N+3: " + offerCancelAndReturn.getSequenceNumber());

            returnInvestments(server, asset, keyPairA, keyPairB, amountA, amountB);
        }


        System.out.println("\n * * * Crowdfunding SUCCESS * * * ");
        System.out.println(" * * * Finish * * * ");
        System.out.println(" * * * A account * * * ");
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);
        System.out.println(" * * * B account * * * ");
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);
        System.out.println(" * * * Holding account * * * ");
        printAccountInfo(new String(keyPairH.getSecretSeed()), server);
        System.out.println(" * * * Target account * * * ");
        printAccountInfo(new String(keyPairT.getSecretSeed()), server);
    }

    private static void issuingCustomAssets(BufferedReader reader, Server server) throws IOException {
        System.out.println("Enter secret seed of SOURCE account:");
        KeyPair sourceKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse sourceAccount = server.accounts().account(sourceKeys);
        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);

        String startingBalance = "100";

        //Transaction 1: Create the issuing account
        KeyPair issuingKeys = KeyPair.random();
        Transaction createIssuingAccount = new Transaction.Builder(sourceAccount)
                .addOperation(new CreateAccountOperation.Builder(issuingKeys, startingBalance).build())
                .build();
        createIssuingAccount.sign(sourceKeys);
        System.out.println("Create issuing account is SUCCESS: " + server.submitTransaction(createIssuingAccount).isSuccess());
        printAccountInfo(new String(issuingKeys.getSecretSeed()), server);
        AccountResponse issuingAccount = server.accounts().account(issuingKeys);

        //Transaction 2: Create the distribution account
        KeyPair distributionKeys = KeyPair.random();
        Transaction createDistributionAccount = new Transaction.Builder(sourceAccount)
                .addOperation(new CreateAccountOperation.Builder(distributionKeys, startingBalance).build())
                .build();
        createDistributionAccount.sign(sourceKeys);
        System.out.println("Create distribution account is success: " + server.submitTransaction(createDistributionAccount).isSuccess());
        printAccountInfo(new String(distributionKeys.getSecretSeed()), server);
        AccountResponse distributionAccount = server.accounts().account(distributionKeys);

        //Asset creation.
        System.out.println("Enter asset NAME:");
        String assetName = reader.readLine();
        Asset asset = Asset.createNonNativeAsset(assetName, issuingKeys);

        //Transaction 3: Creating Trust
        Transaction trustTransaction = new Transaction.Builder(distributionAccount)
                .addOperation(new ChangeTrustOperation.Builder(asset, "10").build())
                .build();
        trustTransaction.sign(distributionKeys);
        System.out.println("Trust transaction is Success: " + server.submitTransaction(trustTransaction).isSuccess());
        System.out.println("Distribution account: ");
        printAccountInfo(new String(distributionKeys.getSecretSeed()), server);

        //Transaction 4: Asset Creation. Payment.
        Transaction paymentTransaction = new Transaction.Builder(issuingAccount)
                .addOperation(new PaymentOperation.Builder(distributionKeys, asset, "10").build())
                .build();
        paymentTransaction.sign(issuingKeys);
        System.out.println("Payment submit: " + server.submitTransaction(paymentTransaction).isSuccess());

        System.out.println("Issuing account: ");
        printAccountInfo(new String(issuingKeys.getSecretSeed()), server);
        System.out.println("Distribution account: ");
        printAccountInfo(new String(distributionKeys.getSecretSeed()), server);
        System.out.println("Source account: ");
        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);

    }

    private static void createOffer(BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * CREATE OFFER * * * ");

        //Receiving account (prepare).
        System.out.println("Enter secret seed of OFFER OWNER account:");
        KeyPair offerOwnerKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse offerOwnerAccount = server.accounts().account(offerOwnerKeys);

        System.out.println("Enter sellingAsset:");
        Asset sellingAsset = assetCreation(reader);
        System.out.println("Enter buyingAsset:");
        Asset buyingAsset = assetCreation(reader);
        System.out.println("Enter amount:");
        String amount = reader.readLine();
        System.out.println("Enter price:");
        String price = reader.readLine();
        System.out.println("Enter offerId:");
        long offerId = Long.parseLong(reader.readLine());

        ManageOfferOperation offerOperation = new ManageOfferOperation.Builder(sellingAsset, buyingAsset, amount, price)
                .setOfferId(offerId)
                .build();

        Transaction offerTransaction = new Transaction.Builder(offerOwnerAccount)
                .addOperation(offerOperation)
                .build();

        offerTransaction.sign(offerOwnerKeys);

        System.out.println("Offer transaction is SUCCESS: " + server.submitTransaction(offerTransaction).isSuccess());
        printAccountInfo(new String(offerOwnerKeys.getSecretSeed()), server);

    }

    private static void sendAsset(BufferedReader reader, Server server) throws IOException {
        Asset asset = assetCreation(reader);

        System.out.println(" * * * SEND ASSET * * * ");

        System.out.println("Enter secret seed of SOURCE account:");
        KeyPair sourceKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse sourceAccount = server.accounts().account(sourceKeys);
        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);

        System.out.println("Enter secret seed of RECEIVING account:");
        KeyPair receivingKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse receivingAccount = server.accounts().account(receivingKeys);
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);

        //Payment transaction.
        System.out.println("Enter sum:");
        String sum = reader.readLine();

        Transaction sendAsset = new Transaction.Builder(sourceAccount)
                .addOperation(new PaymentOperation.Builder(receivingKeys, asset, sum).build())
                .build();
        sendAsset.sign(sourceKeys);
        System.out.println("Payment submit: " + server.submitTransaction(sendAsset).isSuccess());

        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);

    }

    private static void createAccountFriendbot(Server server) throws IOException {
        KeyPair keyPair = KeyPair.random();
        String friendbotUrl = String.format("https://friendbot.stellar.org/?addr=%s", keyPair.getAccountId());
        InputStream response = new URL(friendbotUrl).openStream();
        String body = new Scanner(response, "UTF-8").useDelimiter("\\A").next();

        System.out.print("SUCCESS account creation!");
        printAccountInfo(new String(keyPair.getSecretSeed()), server);
    }

    private static void createAccountAnotherAccount(String secretSeedSourceAccount, BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * Account creation from another account * * * ");
        KeyPair sourceKeys = KeyPair.fromSecretSeed(secretSeedSourceAccount);
        AccountResponse sourceAccount = server.accounts().account(sourceKeys);
        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);

        KeyPair destinationKeyPair = KeyPair.random();
        System.out.println("Enter starting balance");
        String startingBalance = reader.readLine();

        Transaction transaction = new Transaction.Builder(sourceAccount)
                .addOperation(new CreateAccountOperation.Builder(destinationKeyPair, startingBalance).build())
                .build();
        transaction.sign(sourceKeys);
        System.out.println("Create new account: " + server.submitTransaction(transaction).isSuccess());
        printAccountInfo(new String(destinationKeyPair.getSecretSeed()), server);
    }

    private static void printAccountInfo(String secretSeed, Server server) throws IOException {
        KeyPair keyPair = KeyPair.fromSecretSeed(secretSeed);
        AccountResponse account = server.accounts().account(keyPair);

        System.out.println("Account info: \n" + "   [account id: " + keyPair.getAccountId() +
                "\n   secret seed: " + new String(keyPair.getSecretSeed()));

        for (AccountResponse.Signer signer : account.getSigners()) {
            System.out.println(String.format(
                    "   [AccountId: %s, Weight: %s]",
                    signer.getAccountId(),
                    signer.getWeight()));
        }

        System.out.println(String.format(
                "   [LowThreshold: %s, MedThreshold: %s, HighThreshold: %s]",
                account.getThresholds().getLowThreshold(),
                account.getThresholds().getMedThreshold(),
                account.getThresholds().getHighThreshold()));

        for (AccountResponse.Balance balance : account.getBalances()) {
            System.out.println(String.format(
                    "   [Type: %s, Code: %s, Balance: %s, Limit: %s]",
                    balance.getAssetType(),
                    balance.getAssetCode(),
                    balance.getBalance(),
                    balance.getLimit()));
        }

        OffersRequestBuilder offersRequestBuilder = server.offers().forAccount(keyPair);
        Page<OfferResponse> p = offersRequestBuilder.execute();
        List<OfferResponse> list = p.getRecords();
        for (OfferResponse offerResponse : list) {
            System.out.println(String.format(
                    "   [Offer: Id: %s, Amount: %s, Price: %s, Seller: %s, Selling: %s, Buying: %s, PagingToken: %s]",
                    offerResponse.getId(),
                    offerResponse.getAmount(),
                    offerResponse.getPrice(),
                    offerResponse.getSeller(),
                    offerResponse.getSelling().getType(),
                    offerResponse.getBuying().getType(),
                    offerResponse.getPagingToken()));
        }
        System.out.println("   [Sequence number: " + account.getSequenceNumber() + "]");

    }

    private static void printOffersId(String secretSeed, Server server) throws IOException {
        KeyPair offerOwnerKeys = KeyPair.fromSecretSeed(secretSeed);

        OffersRequestBuilder offersRequestBuilder = server.offers().forAccount(offerOwnerKeys);
        Page<OfferResponse> p = offersRequestBuilder.execute();
        List<OfferResponse> list = p.getRecords();
        for (OfferResponse offerResponse : list) {
            System.out.println("OfferId: " + offerResponse.getId() + " Offer amount: " + offerResponse.getAmount()
                    + " Offer type: " + offerResponse.getSelling().getType());
        }
    }

    private static Asset assetCreation(BufferedReader reader) throws IOException {
        System.out.println(" * * * ASSET CREATION: * * * ");
        System.out.println("Enter asset name (\"XLM\", ...):");
        String assetName = reader.readLine();

        if (assetName.equals("XLM")) {
            System.out.println("XLM - AssetTypeNative");
            return new AssetTypeNative();
        } else {
            System.out.println("Enter secret seed of ISSUING account:");
            KeyPair issuingKeys = KeyPair.fromSecretSeed(reader.readLine());
            return Asset.createNonNativeAsset(assetName, issuingKeys);
        }
    }

/*
    private static void sendXLM(BufferedReader reader, Server server) throws IOException {
        System.out.println("Enter secret seed of source account:");
        KeyPair source = KeyPair.fromSecretSeed(reader.readLine());
        System.out.println("Enter account id of destination account:");
        KeyPair destination = KeyPair.fromAccountId(reader.readLine());
        System.out.println("Enter sum:");
        String sum = reader.readLine();
        System.out.println("Enter memo info:");
        String memoInfo = reader.readLine();

        //server.accounts().account(destination);

        AccountResponse sourceAccount = server.accounts().account(source);
        Transaction transaction = new Transaction.Builder(sourceAccount)
                .addOperation(new PaymentOperation.Builder(destination, new AssetTypeNative(), sum).build())
                .addMemo(Memo.text(memoInfo))
                .build();
        transaction.sign(source);
        server.submitTransaction(transaction);
    }
*/

    /*
    private static void newInflation(BufferedReader reader, Server server) throws IOException {
        System.out.println("Enter secret seed of ISSUING account:");
        KeyPair keyPair = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse account = server.accounts().account(keyPair);

        Transaction transInflation = new Transaction.Builder(account)
                .addOperation(new InflationOperation())
                .build();
        transInflation.sign(keyPair);
        server.submitTransaction(transInflation);
    }

    private static void setHomeDomain() {
        Transaction setHomeDomain = new Transaction.Builder(issuingAccount)
                .addOperation(new SetOptionsOperation.Builder()
                        .setHomeDomain("yourdomain.com").build())
                        .build();
        setHomeDomain.sign(issuingKeys);
        server.submitTransaction(setHomeDomain);

    }

    private static void createOffer() {
        //Offer creation.
        ManageOfferOperation firstOffer = new ManageOfferOperation.Builder(new AssetTypeNative(),
                asset, "1", "1")
                .setSourceAccount(issuingKeys)
                .setOfferId(0)
                .build();

        //Offer to server.
        Transaction transDistributedAsset = new Transaction.Builder(distributionAccount)
                .addOperation(firstOffer)
                .build();
        transDistributedAsset.sign(distributionKeys);
        server.submitTransaction(transDistributedAsset);
    }
*/

}
