package org.openspaces.rest.data;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CardAccounts {
    private static int COUNT = 10;
    private static String TYPE = "CardAccount";
    private Map<String, SpaceDocument> cardAccounts;


    public static SpaceTypeDescriptor getType(){
        return new SpaceTypeDescriptorBuilder(TYPE)
                .addFixedProperty("accountId", String.class)
                .supportsDynamicProperties(true)
                .idProperty("accountId", false).create();
    }
    public CardAccounts() {
        cardAccounts = new HashMap<String, SpaceDocument>();
        for (Integer i = 0; i < COUNT; i++) {
            SpaceDocument doc = new SpaceDocument(TYPE);
            doc.setProperty("accountId", i.toString());
            doc.setProperty("cardName", "name" + i.toString());
            doc.setProperty("cardBalance", 0 + i);
            cardAccounts.put(i.toString(), doc);
        }
    }

    public SpaceDocument get(String accountId) {
        try {
            Thread.sleep(500 + new Random().nextInt(500));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return cardAccounts.get(accountId);
    }
}
