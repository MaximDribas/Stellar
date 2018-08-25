package com;

import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.ChangeTrustOperation;
import org.stellar.sdk.KeyPair;
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
        System.out.println("\nClick: 1 - create Account, 2 - send XLM, 3 - print account INFO, " +
                "4 - TRUST creation, 5 - send asset, 0 - exit");
        int number = Integer.parseInt(reader.readLine());
        switch (number) {
            case 1:
                createAccount(server);
                break;
            case 2:
                sendXLM(reader, server);
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
                sendNonNativeAsset(reader, server);
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

    private static void createAccount(Server server) throws IOException {
        KeyPair keyPair = KeyPair.random();
        String friendbotUrl = String.format("https://friendbot.stellar.org/?addr=%s", keyPair.getAccountId());
        InputStream response = new URL(friendbotUrl).openStream();
        String body = new Scanner(response, "UTF-8").useDelimiter("\\A").next();

        System.out.print("SUCCESS account creation!");
        printAccountInfo(new String(keyPair.getSecretSeed()), server);
    }

    private static void printAccountInfo(String secretSeed, Server server) throws IOException {
        KeyPair keyPair = KeyPair.fromSecretSeed(secretSeed);
        AccountResponse account = server.accounts().account(keyPair);

        System.out.println("Account info: \n" + "   [account id: " + keyPair.getAccountId() +
                "\n   secret seed: " + new String(keyPair.getSecretSeed()));

        for (AccountResponse.Balance balance : account.getBalances()) {
            System.out.println(String.format(
                    "   [Type: %s, Code: %s, Balance: %s, Limit: %s]",
                    balance.getAssetType(),
                    balance.getAssetCode(),
                    balance.getBalance(),
                    balance.getLimit()));
        }
    }

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

    private static Asset assetCreation(BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * ASSET CREATION: * * * ");
        System.out.println("Enter asset name:");
        String assetName = reader.readLine();
        System.out.println("Enter secret seed of ISSUING account:");
        KeyPair issuingKeys = KeyPair.fromSecretSeed(reader.readLine());

        return Asset.createNonNativeAsset(assetName, issuingKeys);
    }

    private static void changeTrust(BufferedReader reader, Server server) throws IOException {
        System.out.println(" * * * CHANGE TRUST * * * ");
        Asset asset = assetCreation(reader, server);

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

    private static void sendNonNativeAsset(BufferedReader reader, Server server) throws IOException {
        Asset asset = assetCreation(reader, server);

        System.out.println(" * * * SEND NON NATIVE ASSET * * * ");

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
