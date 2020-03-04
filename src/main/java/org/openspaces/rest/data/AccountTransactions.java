package org.openspaces.rest.data;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.metadata.index.SpaceIndexType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AccountTransactions {
    private static int COUNT = 10;
    private static String TYPE = "AccountTransaction";
    private Map<String, SpaceDocument> accountTransaction;

    private static String makeKey(String a, String b){return a + "-" + b;}

    public static SpaceTypeDescriptor getType(){
        return new SpaceTypeDescriptorBuilder(TYPE)
                .addFixedProperty("accountId", String.class)
                .addFixedProperty("transactionId", String.class)
                .supportsDynamicProperties(true)
                .addPropertyIndex("accountID", SpaceIndexType.EQUAL)
                .addPropertyIndex("transactionId", SpaceIndexType.EQUAL)
                .idProperty("id", true).create();
    }

    public AccountTransactions() {
        accountTransaction = new HashMap<String, SpaceDocument>();
        for (Integer i = 0; i < COUNT; i++) {
            for (Integer j = 0; j < COUNT; j++) {
                SpaceDocument doc = new SpaceDocument(TYPE);
                doc.setProperty("accountId", i.toString());
                doc.setProperty("transactionId", j.toString());
                doc.setProperty("transactionDesc", "Description " + i.toString());
                doc.setProperty("transactionAmount", 0 + i);
                accountTransaction.put(makeKey(i.toString() ,j.toString()), doc);
            }
        }
    }

    public SpaceDocument get(String accountId, String transactionId) {
        try {
            Thread.sleep(500 + new Random().nextInt(500));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return accountTransaction.get(makeKey(accountId, transactionId) );
    }
}
