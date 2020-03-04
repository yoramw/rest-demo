/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openspaces.rest.space;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gigaspaces.client.WriteModifiers;
import com.gigaspaces.document.SpaceDocument;

import net.jini.core.lease.Lease;

import org.jsondoc.core.annotation.Api;
import org.jsondoc.core.annotation.ApiMethod;
import org.jsondoc.core.annotation.ApiPathParam;
import org.jsondoc.core.pojo.ApiVerb;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.space.CannotFindSpaceException;
import org.openspaces.rest.data.AccountTransactions;
import org.openspaces.rest.data.Accounts;
import org.openspaces.rest.data.CardAccounts;
import org.openspaces.rest.exceptions.ObjectNotFoundException;
import org.openspaces.rest.exceptions.RestException;
import org.openspaces.rest.exceptions.TypeAlreadyRegisteredException;
import org.openspaces.rest.exceptions.TypeNotFoundException;
import org.openspaces.rest.exceptions.UnsupportedTypeException;
import org.openspaces.rest.utils.ControllerUtils;
import org.openspaces.rest.utils.ErrorMessage;
import org.openspaces.rest.utils.ErrorResponse;
import org.openspaces.rest.utils.ExceptionMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;

/**
 * Spring MVC controller for the RESTful Space API <p/> usage examples: GET:
 * http://localhost:8080/rest/data/Item/_introduce_type?spaceid=customerid <p/>
 * http://localhost:8080/rest/data/Item/1 http://192.168.9.47:8080/rest/data/Item/_criteria?q=data2='common'
 * <p/> Limit result size: http://192.168.9.47:8080/rest/data/Item/_criteria?q=data2='common'&s=10
 * <p/> DELETE: curl -XDELETE http://localhost:8080/rest/data/Item/1 curl -XDELETE
 * http://localhost:8080/rest/data/Item/_criteria?q=id=1 <p/> Limit result size: curl -XDELETE
 * http://localhost:8080/rest/data/Item/_criteria?q=data2='common'&s=5 <p/> POST: curl -XPOST -d
 * '[{"id":"1", "data":"testdata", "data2":"common", "nestedData" : {"nestedKey1":"nestedValue1"}},
 * {"id":"2", "data":"testdata2", "data2":"common", "nestedData" : {"nestedKey2":"nestedValue2"}},
 * {"id":"3", "data":"testdata3", "data2":"common", "nestedData" : {"nestedKey3":"nestedValue3"}}]'
 * http://localhost:8080/rest/data/Item <p/> <p/> The response is a json object: On Sucess: {
 * "status" : "success" } If there is a data: { "status" : "success", "data" : {...} or [{...},
 * {...}] } <p/> On Failure: If Inner error (TypeNotFound/ObjectNotFound) then { "status" : "error",
 * "error": { "message": "some error message" } } If it is a XAP exception: { "status" : "error",
 * "error": { "java.class" : "the exception's class name", "message": "exception.getMessage()" } }
 *
 * @author rafi
 * @since 8.0
 */
@Controller
@Api(name = "Space API", description = "Methods for interacting with space")
public class SpaceAPIController {

    private static final String TYPE_DESCRIPTION = "The type name";

    @Value("${spaceName}")
    public void setSpaceName(String spaceName) {
        ControllerUtils.spaceName = spaceName;
    }

    @Value("${lookupGroups}")
    public void setLookupGroups(String lookupGroups) {
        ControllerUtils.lookupGroups = lookupGroups;
    }

    @Value("${lookupLocators}")
    public void setLookupLocators(String lookupLocators) {
        ControllerUtils.lookupLocators = lookupLocators;
    }

    @Value("${datetime_format}")
    public void setDatetimeFormat(String datetimeFormat) {
        logger.info("Using [" + datetimeFormat + "] as datetime format");
        ControllerUtils.date_format = datetimeFormat;
        ControllerUtils.simpleDateFormat = new SimpleDateFormat(datetimeFormat);
        ControllerUtils.mapper = new ObjectMapper();
        ControllerUtils.mapper.setDateFormat(ControllerUtils.simpleDateFormat);
        ControllerUtils.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private static final String QUERY_PARAM = "query";
    private static final String MAX_PARAM = "max";
    private static final String URL_PREFIX = "/v1";
    private static final String SPACEID_PARAM = "spaceid";

    private static int maxReturnValues = Integer.MAX_VALUE;
    private static final Logger logger = Logger.getLogger(SpaceAPIController.class.getName());

    private static Object emptyObject = new Object();

    private static Accounts accounts = new Accounts();
    private static AccountTransactions accountTransactions = new AccountTransactions();
    private static CardAccounts cardAccounts = new CardAccounts();

    @PostConstruct
    public void init() {
        logger.info("Init started");
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        gigaSpace.getTypeManager().registerTypeDescriptor(accounts.getType());
        gigaSpace.getTypeManager().registerTypeDescriptor(accountTransactions.getType());
        gigaSpace.getTypeManager().registerTypeDescriptor(cardAccounts.getType());
        logger.info("Init completed");
    }

    @ApiMethod(
            path = URL_PREFIX + "/accounts/{accountId}",
            verb = ApiVerb.GET,
            description = "Reads account details from a given account addressed by \"account-id\""
            , produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    @RequestMapping(value = URL_PREFIX + "/accounts/{accountId}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public
    @ResponseBody
    Map<String, Object> getAccounts(
            @PathVariable() @ApiPathParam(name = "accountId", description = "Account ID") String accountId) throws ObjectNotFoundException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("getting account for account id=" + accountId);
        logger.info("getting account for account id=" + accountId);

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SpaceDocument doc;
        try {
            SpaceDocument query = new SpaceDocument("Account");
            query.setProperty("accountId", accountId);
            doc = gigaSpace.read(query);
            if(null == doc){
                doc = accounts.get(accountId);
                gigaSpace.write(doc);
            }
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, "Account");
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(doc.getProperties()), Map.class));
            return result;
        } catch (IOException e) {
            throw new RestException(e.getMessage());
        }
    }

    /**
     * REST GET by ID request handler
     *
     * @return Map<String, Object>
     */
    @ApiMethod(
            path = URL_PREFIX + "accounts/{accountId}/transactions/{transactionId}",
            verb = ApiVerb.GET,
            description = "Reads transaction details from a given transaction addressed by \"transactionId\" on" +
                            "a given account addressed by \"account-id\""
            , produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    @RequestMapping(value = URL_PREFIX + "/accounts/{accountId}/transactions/{transactionId}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public
    @ResponseBody
    Map<String, Object> getAccountTransaction(
            @PathVariable @ApiPathParam(name = "accountId", description = TYPE_DESCRIPTION) String accountId,
            @PathVariable @ApiPathParam(name = "transactionId") String transactionId) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //read by id request
        SpaceDocument doc;
        try {
            SpaceDocument query = new SpaceDocument("AccountTransaction");
            query.setProperty("accountId", accountId);
            query.setProperty("transactionId", transactionId);
            doc = gigaSpace.read(query);
            if(null == doc){
                doc = accountTransactions.get(accountId, transactionId);
                gigaSpace.write(doc);
            }
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, "AccountTransaction");
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(doc.getProperties()), Map.class));
            return result;
        } catch (IOException e) {
            throw new RestException(e.getMessage());
        }
    }


    /**
     * REST GET by ID request handler
     *
     * @return Map<String, Object>
     */
    @ApiMethod(
            path = URL_PREFIX + "/card-accounts/{accountId}",
            verb = ApiVerb.GET,
            description = "Reads details about a card account"
            , produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    @RequestMapping(value = URL_PREFIX + "/card-accounts/{accountId}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public
    @ResponseBody
    Map<String, Object> getCardAccount(
            @PathVariable @ApiPathParam(name = "accountId", description = TYPE_DESCRIPTION) String accountId) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //read by id request
        SpaceDocument doc;
        try {
            SpaceDocument query = new SpaceDocument("CardAccount");
            query.setProperty("accountId", accountId);
            doc = gigaSpace.read(query);
            if(null == doc){
                doc = cardAccounts.get(accountId);
                gigaSpace.write(doc);
            }
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, "CardAccount");
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(doc.getProperties()), Map.class));
            return result;
        } catch (IOException e) {
            throw new RestException(e.getMessage());
        }
    }

    @ApiMethod(
            path = URL_PREFIX + "/card-accounts/{accountId}",
            verb = ApiVerb.DELETE,
            description = "Invalidate Cache account details for a given account addressed by \"account-id\""
            , produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    @RequestMapping(value = URL_PREFIX + "/card-accounts/{accountId}", method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_VALUE})
    public
    @ResponseBody
    Map<String, Object> invalidateCardAccout(@PathVariable @ApiPathParam(name = "accountId", description = TYPE_DESCRIPTION) String accountId) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SpaceDocument doc;
        try {
            SpaceDocument query = new SpaceDocument("CardAccount");
            query.setProperty("accountId", accountId);
            gigaSpace.clear(query);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, "CardAccount");
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        return result;
    }

    @ApiMethod(
            path = URL_PREFIX + "accounts/{accountId}/transactions/{transactionId}",
            verb = ApiVerb.DELETE,
            description = "Invalidate Cache account transaction details for a given account transaction addressed by \"account-id\" and \"transaction-id\""
            , produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    @RequestMapping(value = URL_PREFIX + "accounts/{accountId}/transactions/{transactionId}", method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_VALUE})
    public
    @ResponseBody
    Map<String, Object> invalidateAccoutTransaction(@PathVariable @ApiPathParam(name = "accountId", description = TYPE_DESCRIPTION) String accountId,
                                                    @PathVariable @ApiPathParam(name = "transactionId") String transactionId) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SpaceDocument doc;
        try {
            SpaceDocument query = new SpaceDocument("AccountTransaction");
            query.setProperty("accountId", accountId);
            query.setProperty("transactionId", transactionId);
            gigaSpace.clear(query);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, "AccountTransaction");
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        return result;
    }

    @ApiMethod(
            path = URL_PREFIX + "/accounts/{accountId}",
            verb = ApiVerb.DELETE,
            description = "Invalidate Cache account details for a given account addressed by \"account-id\""
            , produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    @RequestMapping(value = URL_PREFIX + "/accounts/{accountId}", method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_VALUE})
    public
    @ResponseBody
    Map<String, Object> invalidateAccout(@PathVariable @ApiPathParam(name = "accountId", description = TYPE_DESCRIPTION) String accountId) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SpaceDocument doc;
        try {
            SpaceDocument query = new SpaceDocument("Account");
            query.setProperty("accountId", accountId);
            gigaSpace.clear(query);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, "Account");
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        return result;
    }

    private RuntimeException translateDataAccessException(GigaSpace gigaSpace, DataAccessException e, String type) {
        if (gigaSpace.getTypeManager().getTypeDescriptor(type) == null) {
            return new TypeNotFoundException(type);
        } else {
            return e;
        }
    }

    /**
     * TypeNotFoundException Handler, returns an error response to the client
     */
    @ExceptionHandler(TypeNotFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public
    @ResponseBody
    ErrorResponse resolveTypeDescriptorNotFoundException(TypeNotFoundException e) throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("type descriptor for typeName: " + e.getTypeName() + " not found, returning error response");

        return new ErrorResponse(new ErrorMessage("Type: " + e.getTypeName() + " is not registered in space"));
    }


    /**
     * ObjectNotFoundException Handler, returns an error response to the client
     */
    @ExceptionHandler(ObjectNotFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public
    @ResponseBody
    ErrorResponse resolveDocumentNotFoundException(ObjectNotFoundException e) throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("space id query has no results, returning error response: " + e.getMessage());

        return new ErrorResponse(new ErrorMessage(e.getMessage()));
    }

    /**
     * DataAcessException Handler, returns an error response to the client
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public
    @ResponseBody
    ErrorResponse resolveDataAccessException(DataAccessException e) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received DataAccessException exception", e);

        return new ErrorResponse(new ExceptionMessage(e));

    }

    @ExceptionHandler(CannotFindSpaceException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public
    @ResponseBody
    ErrorResponse resolveCannotFindSpaceException(CannotFindSpaceException e) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received CannotFindSpaceException exception", e);

        return new ErrorResponse(new ExceptionMessage(e));
    }

    @ExceptionHandler(TypeAlreadyRegisteredException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public
    @ResponseBody
    ErrorResponse
    resoleTypeAlreadyRegisteredException(TypeAlreadyRegisteredException e) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received TypeAlreadyRegisteredException exception", e);

        return new ErrorResponse(new ErrorMessage("Type: " + e.getTypeName() + " is already introduced to space"));
    }

    @ExceptionHandler(RestException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public
    @ResponseBody
    ErrorResponse resolveRestIntroduceTypeException(RestException e) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received RestException exception", e.getMessage());

        return new ErrorResponse(new ErrorMessage(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public
    @ResponseBody
    ErrorResponse resolveRuntimeException(RuntimeException e) throws IOException {
        if (logger.isLoggable(Level.SEVERE))
            logger.log(Level.SEVERE, "received RuntimeException (unhandled) exception", e.getMessage());

        return new ErrorResponse(new ErrorMessage("Unhandled exception [" + e.getClass() + "]: " + e.toString()));
    }

    @ExceptionHandler(UnsupportedTypeException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public
    @ResponseBody
    ErrorResponse resolveUnsupportedTypeException(UnsupportedTypeException e) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received UnsupportedTypeException exception", e.getMessage());

        return new ErrorResponse(new ErrorMessage(e.getMessage()));
    }

    /**
     * helper method that creates space documents from the httpRequest payload and writes them to
     * space.
     */
    private void createAndWriteDocuments(GigaSpace gigaSpace, String type, String body, WriteModifiers updateModifiers)
            throws TypeNotFoundException {
        logger.info("creating space Documents from payload");
        SpaceDocument[] spaceDocuments = ControllerUtils.createSpaceDocuments(type, body, gigaSpace);
        if (spaceDocuments != null && spaceDocuments.length > 0) {
            try {
                gigaSpace.writeMultiple(spaceDocuments, Lease.FOREVER, updateModifiers);
            } catch (DataAccessException e) {
                throw translateDataAccessException(gigaSpace, e, type);
            }
            if (logger.isLoggable(Level.FINE))
                logger.fine("wrote space documents to space");
        } else {
            if (logger.isLoggable(Level.FINE))
                logger.fine("did not write anything to space");
        }
    }


}
