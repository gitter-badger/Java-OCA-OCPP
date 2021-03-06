package eu.chargetime.ocpp.test;

import eu.chargetime.ocpp.*;
import eu.chargetime.ocpp.feature.profile.*;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.Request;
import eu.chargetime.ocpp.model.core.*;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;

import java.util.Calendar;
import java.util.UUID;

/*
 ChargeTime.eu - Java-OCA-OCPP
 Copyright (C) 2015-2016 Thomas Volden <tv@chargetime.eu>

 MIT License

 Copyright (c) 2016 Thomas Volden

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
public class FakeCentralSystem
{
    private Request receivedRequest;
    private Confirmation receivedConfirmation;
    private Server server;

    private UUID currentSessionIndex;

    private static FakeCentralSystem instance;
    private boolean isRigged;
    private String currentIdentifier;

    public static FakeCentralSystem getInstance () {
        if (instance == null)
            instance = new FakeCentralSystem();

        return instance;
    }

    private FakeCentralSystem() { }

    private <T extends Confirmation> T failurePoint(T confirmation) {
        if (isRigged) {
            isRigged = false;
            return null;
        }
        return confirmation;
    }

    public boolean connected() {
        return currentIdentifier != null;
    }

    public void clientLost() {
        server.closeSession(currentSessionIndex);
    }

    public enum serverType {JSON, SOAP}

    public void started() throws Exception
    {
        started(serverType.JSON);
    }

    private boolean matchServerType(serverType type)
    {
        boolean result = false;
        switch (type) {
            case JSON:
                result = server instanceof JSONServer;
                break;
            case SOAP:
                result = server instanceof SOAPServer;
        }
        return result;
    }

    public void started(serverType type) throws Exception
    {
        if (server != null) {
            if (matchServerType(type)) {
                return;
            } else {
                server.close();
            }
        }

        ServerCoreProfile serverCoreProfile = new ServerCoreProfile(new ServerCoreEventHandler() {
            @Override
            public AuthorizeConfirmation handleAuthorizeRequest(UUID sessionIndex, AuthorizeRequest request) {
                receivedRequest = request;
                AuthorizeConfirmation confirmation = new AuthorizeConfirmation();
                IdTagInfo tagInfo = new IdTagInfo();
                tagInfo.setStatus(AuthorizationStatus.Accepted);
                Calendar calendar = Calendar.getInstance();
                calendar.set(2018, 1, 1, 1, 1, 1);
                tagInfo.setExpiryDate(calendar);
                confirmation.setIdTagInfo(tagInfo);
                return failurePoint(confirmation);
            }

            @Override
            public BootNotificationConfirmation handleBootNotificationRequest(UUID sessionIndex, BootNotificationRequest request) {
                receivedRequest = request;
                BootNotificationConfirmation confirmation = new BootNotificationConfirmation();
                try {
                    confirmation.setInterval(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                confirmation.setCurrentTime(Calendar.getInstance());
                confirmation.setStatus(RegistrationStatus.Accepted);
                return failurePoint(confirmation);
            }

            @Override
            public DataTransferConfirmation handleDataTransferRequest(UUID sessionIndex, DataTransferRequest request) {
                receivedRequest = request;
                DataTransferConfirmation confirmation = new DataTransferConfirmation();
                confirmation.setStatus(DataTransferStatus.Accepted);
                return failurePoint(confirmation);
            }

            @Override
            public HeartbeatConfirmation handleHeartbeatRequest(UUID sessionIndex, HeartbeatRequest request) {
                receivedRequest = request;
                HeartbeatConfirmation confirmation = new HeartbeatConfirmation();
                confirmation.setCurrentTime(Calendar.getInstance());
                return failurePoint(confirmation);
            }

            @Override
            public MeterValuesConfirmation handleMeterValuesRequest(UUID sessionIndex, MeterValuesRequest request) {
                receivedRequest = request;
                return failurePoint(new MeterValuesConfirmation());
            }

            @Override
            public StartTransactionConfirmation handleStartTransactionRequest(UUID sessionIndex, StartTransactionRequest request) {
                receivedRequest = request;
                IdTagInfo tagInfo = new IdTagInfo();
                tagInfo.setStatus(AuthorizationStatus.Accepted);

                StartTransactionConfirmation confirmation = new StartTransactionConfirmation();
                confirmation.setIdTagInfo(tagInfo);
                return failurePoint(confirmation);
            }

            @Override
            public StatusNotificationConfirmation handleStatusNotificationRequest(UUID sessionIndex, StatusNotificationRequest request) {
                receivedRequest = request;
                StatusNotificationConfirmation confirmation = new StatusNotificationConfirmation();
                return failurePoint(confirmation);
            }

            @Override
            public StopTransactionConfirmation handleStopTransactionRequest(UUID sessionIndex, StopTransactionRequest request) {
                receivedRequest = request;
                StopTransactionConfirmation confirmation = new StopTransactionConfirmation();
                return failurePoint(confirmation);
            }
        });

        ServerSmartChargingProfile smartChargingProfile = new ServerSmartChargingProfile(new ServerSmartChargingHandler() {

        });

        ServerRemoteTriggerProfile remoteTriggerProfile = new ServerRemoteTriggerProfile(new ServerRemoteTriggerHandler() {

        });

        int port = 0;
        switch (type) {
            case JSON:
                server = new JSONServer(serverCoreProfile);
                port = 8887;
                break;
            case SOAP:
                server = new SOAPServer(serverCoreProfile);
                port = 8890;
                break;
        }

        server.addFeatureProfile(smartChargingProfile);
        server.addFeatureProfile(remoteTriggerProfile);

        server.open("localhost", port, new ServerEvents() {
            @Override
            public void newSession(UUID sessionIndex, String identifier) {
                currentSessionIndex = sessionIndex;
                currentIdentifier = identifier;
            }

            @Override
            public void lostSession(UUID identity) {
                currentSessionIndex = null;
                currentIdentifier = null;
                // clear
                receivedConfirmation = null;
                receivedRequest = null;
            }
        });
    }

    public boolean hasHandledAuthorizeRequest() {
        return receivedRequest instanceof AuthorizeRequest;
    }

    public void stopped() {
        server.close();
    }

    public boolean hasHandledBootNotification(String vendor, String model) {
        boolean result = receivedRequest instanceof BootNotificationRequest;
        if (result) {
            BootNotificationRequest request = (BootNotificationRequest) this.receivedRequest;
            result &= request.getChargePointVendor().equals(vendor);
            result &= request.getChargePointModel().equals(model);
        }
        return result;
    }

    public void sendChangeAvailabilityRequest(int connectorId, AvailabilityType type) throws Exception {
        ChangeAvailabilityRequest request = new ChangeAvailabilityRequest();
        request.setType(type);
        request.setConnectorId(connectorId);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);

    }

    public boolean hasReceivedChangeAvailabilityConfirmation(String status) {
        boolean result = receivedConfirmation instanceof ChangeAvailabilityConfirmation;
        if (result)
            result &= ((ChangeAvailabilityConfirmation) receivedConfirmation).getStatus().toString().equals(status);
        return result;
    }

    public void sendChangeConfigurationRequest(String key, String value) throws Exception {
        ChangeConfigurationRequest request = new ChangeConfigurationRequest();
        request.setKey(key);
        request.setValue(value);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedChangeConfigurationConfirmation() {
        return receivedConfirmation instanceof ChangeConfigurationConfirmation;
    }

    public void sendClearCacheRequest() throws Exception {
        ClearCacheRequest request = new ClearCacheRequest();
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedClearCacheConfirmation() {
        return receivedConfirmation instanceof ClearCacheConfirmation;
    }

    public void sendDataTransferRequest(String vendorId, String messageId, String data) throws Exception {
        DataTransferRequest request = new DataTransferRequest();
        request.setVendorId(vendorId);
        request.setMessageId(messageId);
        request.setData(data);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedDataTransferConfirmation() {
        return receivedConfirmation instanceof DataTransferConfirmation;
    }

    public boolean hasHandledDataTransferRequest() {
        return receivedRequest instanceof DataTransferRequest;
    }

    public void sendGetConfigurationRequest(String... key) throws Exception {
        GetConfigurationRequest request = new GetConfigurationRequest();
        request.setKey(key);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedGetConfigurationConfirmation() {
        return receivedConfirmation instanceof GetConfigurationConfirmation;
    }

    public boolean hasHandledHeartbeat() {
        return receivedRequest instanceof HeartbeatRequest;
    }

    public boolean hasHandledMeterValuesRequest() {
        return receivedRequest instanceof MeterValuesRequest;
    }

    public void sendRemoteStartTransactionRequest(int connectorId, String idTag) throws Exception {
        RemoteStartTransactionRequest request = new RemoteStartTransactionRequest();
        IdToken idToken = new IdToken();
        idToken.setIdToken(idTag);
        request.setIdTag(idToken);
        request.setConnectorId(connectorId);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedRemoteStartTransactionConfirmation(String status) {
        boolean result = receivedConfirmation instanceof RemoteStartTransactionConfirmation;
        if (result)
            result &= ((RemoteStartTransactionConfirmation) receivedConfirmation).getStatus().toString().equals(status);
        return result;
    }

    public void sendRemoteStopTransactionRequest(int transactionId) throws Exception {
        RemoteStopTransactionRequest request = new RemoteStopTransactionRequest();
        request.setTransactionId(transactionId);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedRemoteStopTransactionConfirmation(String status) {
        boolean result = receivedConfirmation instanceof RemoteStopTransactionConfirmation;
        if (result)
            result &= ((RemoteStopTransactionConfirmation) receivedConfirmation).getStatus().toString().equals(status);
        return result;
    }

    public void sendResetRequest(ResetType type) throws Exception {
        ResetRequest request = new ResetRequest();
        request.setType(type);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedResetConfirmation(String status) {
        boolean result = receivedConfirmation instanceof ResetConfirmation;
        if (result)
            result &= ((ResetConfirmation) receivedConfirmation).getStatus().toString().equals(status);
        return result;
    }

    public boolean hasHandledStartTransactionRequest() {
        return receivedRequest instanceof StartTransactionRequest;
    }

    public boolean hasHandledStatusNotificationRequest() {
        return receivedRequest instanceof StatusNotificationRequest;
    }

    public boolean hasHandledStopTransactionRequest() {
        return receivedRequest instanceof StopTransactionRequest;
    }

    public void sendUnlockConnectorRequest(int connectorId) throws Exception {
        UnlockConnectorRequest request = new UnlockConnectorRequest();
        request.setConnectorId(connectorId);
        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }

    public boolean hasReceivedUnlockConnectorConfirmation(String status) {
        boolean result = receivedConfirmation instanceof UnlockConnectorConfirmation;
        if (result)
            result &= ((UnlockConnectorConfirmation) receivedConfirmation).getStatus().toString().equals(status);
        return result;
    }

    public void isRiggedToFailOnNextRequest() {
        isRigged = true;
    }

    public void sendTriggerMessage(TriggerMessageRequestType type, Integer connectorId) throws Exception {
        TriggerMessageRequest request = new TriggerMessageRequest(type);
        try {
            request.setConnectorId(connectorId);
        } catch (PropertyConstraintException e) {
            e.printStackTrace();
        }

        server.send(currentSessionIndex, request).whenComplete((confirmation, throwable) -> receivedConfirmation = confirmation);
    }
}
