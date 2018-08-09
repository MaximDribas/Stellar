package com;

import org.stellar.sdk.AssetTypeNative;
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
            }
        }
    }

    private static void menu(BufferedReader reader, Server server) throws Exception {
        System.out.println("\nClick: 1 - create an Account, 2 - send money, " +
                "3 - print account info, 4 - print info about all accounts, 0 - exit the program");
        int number = Integer.parseInt(reader.readLine());
        switch (number) {
            case 1:
                createAccount(server);
                break;
            case 2:
                buildTransaction(reader, server);
                break;
            case 3:
                System.out.println("Enter secret seed of source account:");
                String secretSeed = reader.readLine();
                printAccountInfo(secretSeed, server);
                break;
            case 4:
                printAllAccountsInfo(server);
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

    private static void buildTransaction(BufferedReader reader, Server server) throws IOException {
        System.out.println("Enter secret seed of source account:");
        String sourceSecretSeed = reader.readLine();
        System.out.println("Enter account id of destination account:");
        String destinationId = reader.readLine();
        System.out.println("Enter sum:");
        String sum = reader.readLine();
        System.out.println("Enter memo info:");
        String memoInfo = reader.readLine();

        KeyPair source = KeyPair.fromSecretSeed(sourceSecretSeed);
        KeyPair destination = KeyPair.fromAccountId(destinationId);

        server.accounts().account(destination);

        AccountResponse sourceAccount = server.accounts().account(source);

        Transaction transaction = new Transaction.Builder(sourceAccount)
                .addOperation(new PaymentOperation.Builder(destination, new AssetTypeNative(), sum).build())
                .addMemo(Memo.text(memoInfo))
                .build();
        transaction.sign(source);

        try {
            server.submitTransaction(transaction);
            System.out.println("SUCCESS transaction!");
        } catch (Exception e) {
            System.out.println("Something went wrong!");
            System.out.println(e.getMessage());
        }
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
