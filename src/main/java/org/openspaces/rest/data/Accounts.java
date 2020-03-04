package org.openspaces.rest.data;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.metadata.index.SpaceIndexType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Accounts {
    private static int COUNT = 10;
    private static String TYPE = "Account";
    private Map<String, SpaceDocument> accounts;

    public static SpaceTypeDescriptor getType(){
        return new SpaceTypeDescriptorBuilder(TYPE)
                .addFixedProperty("accountId", String.class)
                .supportsDynamicProperties(true)
                .idProperty("accountId", false).create();
    }

    public Accounts() {
        accounts = new HashMap<String, SpaceDocument>();
        for (Integer i = 0; i < COUNT; i++) {
            SpaceDocument doc = new SpaceDocument(TYPE);
            doc.setProperty("accountId", i.toString());
            doc.setProperty("accountName", "name" + i.toString());
            doc.setProperty("accountBalance", 0 + i);
            accounts.put(i.toString(), doc);
        }
    }

    public SpaceDocument get(String accountId) {
        try {
            Thread.sleep(500 + new Random().nextInt(500));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return accounts.get(accountId);
    }
}
