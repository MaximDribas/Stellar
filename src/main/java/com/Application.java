package com;

import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Memo;
import org.stellar.sdk.Network;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.Transaction;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

public class Application {
    public static void main(String[] args) throws IOException {
        Network.useTestNetwork();

        KeyPair keyPair1 = KeyPair.random();
        AccountResponse account1 = createAccount(keyPair1);

        System.out.println("\n * * * * * * * * * * * * * * * * * * * * * * \n");

        KeyPair keyPair2 = KeyPair.random();
        AccountResponse account2 = createAccount(keyPair2);

        System.out.println("\n * * * * * * * * * * * * * * * * * * * * * * \n");

        checkBalance(account1);
        checkBalance(account2);

        System.out.println("\n * * * * * * * * * * * * * * * * * * * * * * \n");

        buildTransaction(keyPair1, keyPair2);

        System.out.println("\n * * * * * * * * * * * * * * * * * * * * * * \n");

        checkBalance(account1);
        checkBalance(account2);
    }

    private static void buildTransaction(KeyPair keyPair1, KeyPair keyPair2) throws IOException {
        Server server = new Server("https://horizon-testnet.stellar.org");

        KeyPair source = KeyPair.fromSecretSeed(keyPair1.getSecretSeed());
        KeyPair destination = KeyPair.fromAccountId(keyPair2.getAccountId());

        server.accounts().account(destination);

        AccountResponse sourceAccount = server.accounts().account(source);

        Transaction transaction = new Transaction.Builder(sourceAccount)
                .addOperation(new PaymentOperation.Builder(destination, new AssetTypeNative(), "10").build())
                .addMemo(Memo.text("Memo: Test Transaction"))
                .build();
        transaction.sign(source);

        try {
            SubmitTransactionResponse response = server.submitTransaction(transaction);
            System.out.println("!!!SUCCESS transaction");
            System.out.println(response);
        } catch (Exception e) {
            System.out.println("Something went wrong!");
            System.out.println(e.getMessage());
        }

    }

    private static AccountResponse createAccount(KeyPair keyPair) throws IOException {
        String friendbotUrl = String.format("https://friendbot.stellar.org/?addr=%s", keyPair.getAccountId());
        InputStream response = new URL(friendbotUrl).openStream();
        String body = new Scanner(response, "UTF-8").useDelimiter("\\A").next();
        System.out.println("!!!SUCCESS new account\n" + body);
        //Getting the account’s details.
        Server server = new Server("https://horizon-testnet.stellar.org");
        AccountResponse account = server.accounts().account(keyPair);

        return account;
    }

    private static void checkBalance(AccountResponse account) {
        System.out.println("!!!BALANCES for account " + account.getKeypair().getAccountId());
        for (AccountResponse.Balance balance : account.getBalances()) {
            System.out.println(String.format(
                    "Type: %s, Code: %s, Balance: %s",
                    balance.getAssetType(),
                    balance.getAssetCode(),
                    balance.getBalance()));
        }

    }

}
