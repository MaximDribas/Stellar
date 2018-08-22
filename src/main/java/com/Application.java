package com;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.ChangeTrustOperation;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.ManageOfferOperation;
import org.stellar.sdk.Memo;
import org.stellar.sdk.Network;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Application {
    private static Map<String, String> accountMap = new HashMap<String, String>();
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
        System.out.println("\nClick: 1 - create an Account, 2 - send lumens, 3 - print account info, \n" +
                "4 - print info about all accounts, 5 - create and send an asset, 0 - exit the program");
        int number = Integer.parseInt(reader.readLine());
        switch (number) {
            case 1:
                createAccount(server);
                break;
            case 2:
                sendLumens(reader, server);
                break;
            case 3:
                System.out.println("Enter secret seed of source account:");
                String secretSeed = reader.readLine();
                printAccountInfo(secretSeed, server);
                break;
            case 4:
                printAllAccountsInfo(server);
                break;
            case 5:
                createAndSendCustomAsset(reader, server);
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

    private static void sendLumens(BufferedReader reader, Server server) throws IOException {
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

    private static void createAndSendCustomAsset(BufferedReader reader, Server server) throws IOException {
        System.out.println("Enter secret seed of ISSUING account:");
        KeyPair issuingKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse issuingAccount = server.accounts().account(issuingKeys);

        System.out.println("Enter secret seed of DISTRIBUTION account:");
        KeyPair distributionKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse distributionAccount = server.accounts().account(distributionKeys);

        System.out.println("Enter secret seed of RECEIVING account:");
        KeyPair receivingKeys = KeyPair.fromSecretSeed(reader.readLine());
        AccountResponse receivingAccount = server.accounts().account(receivingKeys);

        System.out.println("Enter asset name:");
        String assetName = reader.readLine();
        System.out.println("Enter trust limit:");
        String trustLimit = reader.readLine();
        System.out.println("Enter sending sum:");
        String sendingSum = reader.readLine();

        //Asset creation.
        Asset asset = Asset.createNonNativeAsset(assetName, issuingKeys);

        //Trust creation.
        Transaction trustAsset = new Transaction.Builder(distributionAccount)
                .addOperation(new ChangeTrustOperation.Builder(asset, trustLimit).build())
                .build();
        trustAsset.sign(distributionKeys);
        server.submitTransaction(trustAsset);

        //Send asset (issuing-distributor).
        Transaction sendAsset_issuingToDistributor = new Transaction.Builder(issuingAccount)
                .addOperation(new PaymentOperation.Builder(distributionKeys, asset, sendingSum).build())
                .build();
        sendAsset_issuingToDistributor.sign(issuingKeys);
        server.submitTransaction(sendAsset_issuingToDistributor);

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

        //Payment creation (distributor-receiver)
        Transaction sendAsset_distributorToReceiver = new Transaction.Builder(distributionAccount)
                .addOperation(new PaymentOperation.Builder(receivingKeys, asset, "1").build())
                .build();
        sendAsset_distributorToReceiver.sign(distributionKeys);
        server.submitTransaction(sendAsset_distributorToReceiver);
    }

    private static void createAccount(Server server) throws IOException {
        KeyPair keyPair = KeyPair.random();
        String friendbotUrl = String.format("https://friendbot.stellar.org/?addr=%s", keyPair.getAccountId());
        InputStream response = new URL(friendbotUrl).openStream();
        String body = new Scanner(response, "UTF-8").useDelimiter("\\A").next();

        System.out.print("SUCCESS account creation!");
        printAccountInfo(new String(keyPair.getSecretSeed()), server);
        accountMap.put(keyPair.getAccountId(), new String(keyPair.getSecretSeed()));
    }

    private static void printAccountInfo(String secretSeed, Server server) throws IOException {
        KeyPair keyPair = KeyPair.fromSecretSeed(secretSeed);
        AccountResponse account = server.accounts().account(keyPair);

        System.out.println("\nAccount info: \n" + "   [account id: " + keyPair.getAccountId() +
                "\n   secret seed: " + new String(keyPair.getSecretSeed()));

        for (AccountResponse.Balance balance : account.getBalances()) {
            System.out.println(String.format(
                    "   Type: %s, Code: %s, Balance: %s]",
                    balance.getAssetType(),
                    balance.getAssetCode(),
                    balance.getBalance()));
        }
    }

    private static void printAllAccountsInfo(Server server) throws IOException {
        for (Map.Entry<String, String> entry : accountMap.entrySet()) {
            printAccountInfo(entry.getValue(), server);
        }
    }
}
