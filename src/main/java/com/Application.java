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
        AccountResponse sourceAccount = createAccount(keyPair1);
        KeyPair keyPair2 = KeyPair.random();
        AccountResponse destinationAccount = createAccount(keyPair2);

        checkBalance(sourceAccount);
        checkBalance(destinationAccount);
        System.out.println(" + + + + + + + + + + + + + ");

        buildTransaction(keyPair1, keyPair2);

        System.out.println(" + + + + + + + + + + + + + ");
        checkBalance(sourceAccount);
        checkBalance(destinationAccount);
    }

    private static void buildTransaction(KeyPair keyPair1, KeyPair keyPair2) throws IOException {
        Server server = new Server("https://horizon-testnet.stellar.org");

        KeyPair source = KeyPair.fromSecretSeed(keyPair1.getSecretSeed());
        KeyPair destination = KeyPair.fromAccountId(keyPair2.getAccountId());

        // First, check to make sure that the destination account exists.
        // You could skip this, but if the account does not exist, you will be charged
        // the transaction fee when the transaction fails.

        // It will throw HttpResponseException if account does not exist or there was another error.
        server.accounts().account(destination);

        // If there was no error, load up-to-date information on your account.
        AccountResponse sourceAccount = server.accounts().account(source);

        // Start building the transaction.
        Transaction transaction = new Transaction.Builder(sourceAccount)
                .addOperation(new PaymentOperation.Builder(destination, new AssetTypeNative(), "10").build())
                // A memo allows you to add your own metadata to a transaction. It's
                // optional and does not affect how Stellar treats the transaction.
                .addMemo(Memo.text("Memo: Test Transaction"))
                .build();
        // Sign the transaction to prove you are actually the person sending it.
        transaction.sign(source);

        // And finally, send it off to Stellar!
        try {
            System.out.println(transaction.getMemo().toString());
            SubmitTransactionResponse response = server.submitTransaction(transaction);
            System.out.println("Success!");
            System.out.println(response);
        } catch (Exception e) {
            System.out.println("Something went wrong!");
            System.out.println(e.getMessage());
            // If the result is unknown (no response body, timeout etc.) we simply resubmit
            // already built transaction:
            // SubmitTransactionResponse response = server.submitTransaction(transaction);
        }

    }

    private static AccountResponse createAccount(KeyPair keyPair) throws IOException {
        //KeyPair keyPair = KeyPair.random();


        //Create a test account
        String friendbotUrl = String.format("https://friendbot.stellar.org/?addr=%s", keyPair.getAccountId());
        InputStream response = new URL(friendbotUrl).openStream();
        String body = new Scanner(response, "UTF-8").useDelimiter("\\A").next();
        System.out.println("SUCCESS! You have a new account :)\n" + body);

        //Getting the account’s details.
        Server server = new Server("https://horizon-testnet.stellar.org");
        AccountResponse account = server.accounts().account(keyPair);

        return account;
    }

    private static void checkBalance(AccountResponse account) {
        System.out.println("Balances for account " + account.getKeypair().getAccountId());
        for (AccountResponse.Balance balance : account.getBalances()) {
            System.out.println(String.format(
                    "Type: %s, Code: %s, Balance: %s",
                    balance.getAssetType(),
                    balance.getAssetCode(),
                    balance.getBalance()));
        }

    }

}
