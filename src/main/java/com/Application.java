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
                " 4 - change TRUST,\n 5 - send ASSET, 6 - send SSC operation, 7 - SSC crowdfunding, 8 - issuing Custom Assets, " +
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
                createSendSSCOperation(reader, server);
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

    private static void crowdfundingSSC(BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * CROWDFUNDING SSC * * * ");

        System.out.println("Enter secret seed of party A account:");
        KeyPair keyPairA = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse accountA = server.accounts().account(keyPairA);
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);
        long offerIdA = 0;

        System.out.println("Enter secret seed of party B account:");
        KeyPair keyPairB = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse accountB = server.accounts().account(keyPairB);
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);
        long offerIdB = 0;

        System.out.println("Enter secret seed of TARGET account:");
        KeyPair targetKeyPair = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse targetAccount = server.accounts().account(targetKeyPair);
        printAccountInfo(new String(targetKeyPair.getSecretSeed()), server);
        long offerIdTarget = 0;

        //Transaction 1: Create the Holding Account
        KeyPair holdingKeyPair = KeyPair.random();

        Transaction createHoldingAccount = new Transaction.Builder(accountA)
                .addOperation(new CreateAccountOperation.Builder(holdingKeyPair, "100").build())
                .build();
        createHoldingAccount.sign(keyPairA);
        System.out.println("Created the Holding account: " + server.submitTransaction(createHoldingAccount).isSuccess());
        printAccountInfo(new String(holdingKeyPair.getSecretSeed()), server);

        AccountResponse holdingAccount = server.accounts().account(holdingKeyPair);
        long offerIdHolding = 0;

        //Transaction 2: Add signers
        Transaction transactionSigners = new Transaction.Builder(holdingAccount)
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
        System.out.println("Operations length: " + transactionSigners.getOperations().length);
        System.out.println("Before sign Signatures size: " + transactionSigners.getSignatures().size());
        transactionSigners.sign(holdingKeyPair);
        System.out.println("After sign Signatures size: " + transactionSigners.getSignatures().size());
        System.out.println("Signers transaction submit: " + server.submitTransaction(transactionSigners).isSuccess());

        //After Transaction 2, the holding account should be funded with the tokens to be used for the crowdfunding
        //campaign, as well as with enough lumens to cover the transaction fees for all of the following transactions.
        String assetName = "crowdAsset15";
        Asset asset = Asset.createNonNativeAsset(assetName, keyPairA);
        System.out.println(" * * * Asset created: " + assetName);
        String amountV = "10";
        String amountA = "4";
        String amountB = "4";

        Transaction trustTransaction = new Transaction.Builder(holdingAccount)
                .addOperation(new ChangeTrustOperation.Builder(asset, amountV).build())
                .build();
        trustTransaction.sign(keyPairA);
        trustTransaction.sign(keyPairB);
        System.out.println("Trust transaction: " + server.submitTransaction(trustTransaction).isSuccess());
        printAccountInfo(new String(holdingKeyPair.getSecretSeed()), server);

        Transaction issuingAsset = new Transaction.Builder(accountA)
                .addOperation(new PaymentOperation.Builder(holdingKeyPair, asset, amountV).build())
                .build();
        issuingAsset.sign(keyPairA);
        System.out.println("Payment/Issuing submit: " + server.submitTransaction(issuingAsset).isSuccess());
        printAccountInfo(new String(holdingKeyPair.getSecretSeed()), server);

        //Transaction 3: Begin Crowdfunding
        ManageOfferOperation offerOperation = new ManageOfferOperation.Builder(asset, new AssetTypeNative(), amountV, "1")
                .setOfferId(offerIdHolding)
                .build();
        Transaction offerTransaction = new Transaction.Builder(holdingAccount)
                .addOperation(offerOperation)
                .build();
        offerTransaction.sign(keyPairA);
        offerTransaction.sign(keyPairB);
        offerIdHolding++;
        System.out.println("Offer submit: " + server.submitTransaction(offerTransaction).isSuccess() +
                "\nofferIdHolding: " + offerOperation.getOfferId());
        printAccountInfo(new String(holdingKeyPair.getSecretSeed()), server);

        //Transaction 4: Crowdfunding Succeeds

        Transaction sendAssetV = new Transaction.Builder(holdingAccount)
                .addOperation(new PaymentOperation.Builder(targetKeyPair, new AssetTypeNative(), amountV).build())
                .build();
        sendAssetV.sign(keyPairA);
        sendAssetV.sign(keyPairB);

        //Transaction 5: Crowdfunding Fails
        System.out.println(" * * * Crowdfunding Fails * * * ");
        //offerCancelTransaction
        ManageOfferOperation offerCancelOperation = new ManageOfferOperation.Builder(asset, new AssetTypeNative(), "0", "1")
                .setOfferId(offerOperation.getOfferId())
                .build();
        Transaction offerCancelTransaction = new Transaction.Builder(holdingAccount)
                .addOperation(offerCancelOperation)
                .build();
        offerCancelTransaction.sign(keyPairA);
        offerCancelTransaction.sign(keyPairB);
        System.out.println("offerIdHolding: " + offerCancelOperation.getOfferId());

        //Transaction 5+: offerReturnTransaction
        ManageOfferOperation offerReturnAOperation = new ManageOfferOperation.Builder(new AssetTypeNative(), asset, amountA, "1")
                .setOfferId(offerIdHolding)
                .build();
        Transaction offerReturnA = new Transaction.Builder(holdingAccount)
                .addOperation(offerReturnAOperation)
                .build();
        offerReturnA.sign(keyPairA);
        offerReturnA.sign(keyPairB);
        offerIdHolding++;
        System.out.println("offerReturnAOperation ID: " + offerReturnAOperation.getOfferId());


        ManageOfferOperation offerReturnBOperation = new ManageOfferOperation.Builder(new AssetTypeNative(), asset, amountB, "1")
                .setOfferId(offerIdHolding)
                .build();
        Transaction offerReturnB = new Transaction.Builder(holdingAccount)
                .addOperation(offerReturnBOperation)
                .build();
        offerReturnB.sign(keyPairA);
        offerReturnB.sign(keyPairB);
        offerIdHolding++;
        System.out.println("offerReturnBOperation ID: " + offerReturnBOperation.getOfferId());

        //Transaction 0: Send investment
        System.out.println(" * * * trustTransactionA * * * ");
        Transaction trustTransactionA = new Transaction.Builder(accountA)
                .addOperation(new ChangeTrustOperation.Builder(asset, amountA).build())
                .build();
        trustTransactionA.sign(keyPairA);
        System.out.println("Trust transaction A: " + server.submitTransaction(trustTransactionA).isSuccess());
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);

        System.out.println(" * * * sendInvestmentA * * * ");
        Transaction sendInvestmentA = new Transaction.Builder(accountA)
                .addOperation(new ManageOfferOperation.Builder(new AssetTypeNative(), asset, amountA, "1")
                        .setOfferId(0)
                        .build())
                .build();
        sendInvestmentA.sign(keyPairA);
        System.out.println("Send investment A submit: " + server.submitTransaction(sendInvestmentA).isSuccess());
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);

        System.out.println(" * * * trustTransactionB * * * ");
        Transaction trustTransactionB = new Transaction.Builder(accountB)
                .addOperation(new ChangeTrustOperation.Builder(asset, amountB).build())
                .build();
        trustTransactionB.sign(keyPairB);
        System.out.println("Trust transaction B: " + server.submitTransaction(trustTransactionB).isSuccess());
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);

        System.out.println(" * * * sendInvestmentB * * * ");
        Transaction sendInvestmentB = new Transaction.Builder(accountB)
                .addOperation(new ManageOfferOperation.Builder(new AssetTypeNative(), asset, amountB, "1")
                        .setOfferId(0)
                        .build())
                .build();
        sendInvestmentB.sign(keyPairB);
        System.out.println("Send investment B submit: " + server.submitTransaction(sendInvestmentB).isSuccess());
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);

        //Finish
        if (Integer.parseInt(amountA) + Integer.parseInt(amountB) == 10) {
            System.out.println("Payment V submit: " + server.submitTransaction(sendAssetV).isSuccess());
            printAccountInfo(new String(targetKeyPair.getSecretSeed()), server);
        } else {
            System.out.println("Offer cancel transaction submit: " + server.submitTransaction(offerCancelTransaction).isSuccess());
            System.out.println("Offer return A submit: " + server.submitTransaction(offerReturnA).isSuccess());
            System.out.println("Offer return B submit: " + server.submitTransaction(offerReturnB).isSuccess());
            printAccountInfo(new String(holdingKeyPair.getSecretSeed()), server);
        }

        System.out.println(" * * * Finish * * * ");
        printAccountInfo(new String(keyPairA.getSecretSeed()), server);
        printAccountInfo(new String(keyPairB.getSecretSeed()), server);
        printAccountInfo(new String(holdingKeyPair.getSecretSeed()), server);
        printAccountInfo(new String(targetKeyPair.getSecretSeed()), server);

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
        Asset asset = Asset.createNonNativeAsset("asset4", issuingKeys);

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

    private static void createSendSSCOperation(BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * CREATE SEND OPERATION * * * ");

        Asset asset = assetCreation(reader);

        //Accounts (prepare).
        System.out.println("Enter secret seed of SOURCE account:");
        KeyPair sourceKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse sourceAccount = server.accounts().account(sourceKeys);
        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);

        System.out.println("Enter secret seed of RECEIVING account:");
        KeyPair receivingKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse receivingAccount = server.accounts().account(receivingKeys);
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);

        //Signers transaction.
        Transaction transactionSignersSSC = new Transaction.Builder(sourceAccount)
                .addOperation(new SetOptionsOperation.Builder()
                        .setSigner(receivingKeys.getXdrSignerKey(), 2).build())
                .addOperation(new SetOptionsOperation.Builder()
                        .setMasterKeyWeight(2)
                        .setLowThreshold(4)
                        .setMediumThreshold(4)
                        .setHighThreshold(4)
                        .build())
                .build();
        System.out.println("Operations length: " + transactionSignersSSC.getOperations().length);
        System.out.println("Before sign Signatures size: " + transactionSignersSSC.getSignatures().size());
        transactionSignersSSC.sign(sourceKeys);
        transactionSignersSSC.sign(receivingKeys);
        System.out.println("After sign Signatures size: " + transactionSignersSSC.getSignatures().size());
        System.out.println("Sign submit: " + server.submitTransaction(transactionSignersSSC).isSuccess());

        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);

        //Payment transaction.
        System.out.println("Enter sum:");
        String sum = reader.readLine();

        Transaction transactionSendSSC = new Transaction.Builder(sourceAccount)
                .addOperation(new PaymentOperation.Builder(receivingKeys, asset, sum).build())
                .build();
        System.out.println("Operations length: " + transactionSendSSC.getOperations().length);
        System.out.println("Before sign Signatures size: " + transactionSendSSC.getSignatures().size());
        transactionSendSSC.sign(sourceKeys);
        transactionSendSSC.sign(receivingKeys);
        System.out.println("After sign Signatures size: " + transactionSendSSC.getSignatures().size());
        System.out.println("Send submit: " + server.submitTransaction(transactionSendSSC).isSuccess());

        printAccountInfo(new String(sourceKeys.getSecretSeed()), server);
        printAccountInfo(new String(receivingKeys.getSecretSeed()), server);

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
                    "   [Offer: Id: %s, Selling: %s, Buying: %s, Amount: %s, Price: %s, PagingToken: %s, Seller: %s]",
                    offerResponse.getId(),
                    offerResponse.getAmount(),
                    offerResponse.getPrice(),
                    offerResponse.getSelling(),
                    offerResponse.getBuying(),
                    offerResponse.getPagingToken(),
                    offerResponse.getSeller()));
        }

    }

    private static long getOneOfferId(String secretSeed, Server server) throws IOException {
        long offerId = 0;
        KeyPair offerOwnerKeys = KeyPair.fromSecretSeed(secretSeed);

        OffersRequestBuilder offersRequestBuilder = server.offers().forAccount(offerOwnerKeys);
        Page<OfferResponse> p = offersRequestBuilder.execute();
        List<OfferResponse> list = p.getRecords();
        for (OfferResponse offerResponse : list) {
            offerId = offerResponse.getId();
        }
        return offerId;
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
