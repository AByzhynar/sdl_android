package com.sdl.app_services_consumer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCRequest;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.TTSChunkFactory;
import com.smartdevicelink.proxy.rpc.AddCommand;
import com.smartdevicelink.proxy.rpc.AppServiceData;
import com.smartdevicelink.proxy.rpc.AppServiceManifest;
import com.smartdevicelink.proxy.rpc.AppServiceRecord;
import com.smartdevicelink.proxy.rpc.ButtonPress;
import com.smartdevicelink.proxy.rpc.ButtonPressResponse;
import com.smartdevicelink.proxy.rpc.GetAppServiceData;
import com.smartdevicelink.proxy.rpc.GetAppServiceDataResponse;
import com.smartdevicelink.proxy.rpc.GetFile;
import com.smartdevicelink.proxy.rpc.GetFileResponse;
import com.smartdevicelink.proxy.rpc.MediaServiceData;
import com.smartdevicelink.proxy.rpc.MenuParams;
import com.smartdevicelink.proxy.rpc.OnAppServiceData;
import com.smartdevicelink.proxy.rpc.OnCommand;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnSystemCapabilityUpdated;
import com.smartdevicelink.proxy.rpc.PerformAppServiceInteraction;
import com.smartdevicelink.proxy.rpc.PerformAppServiceInteractionResponse;
import com.smartdevicelink.proxy.rpc.PublishAppService;
import com.smartdevicelink.proxy.rpc.PublishAppServiceResponse;
import com.smartdevicelink.proxy.rpc.SendLocation;
import com.smartdevicelink.proxy.rpc.SendLocationResponse;
import com.smartdevicelink.proxy.rpc.Speak;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.AppServiceType;
import com.smartdevicelink.proxy.rpc.enums.ButtonName;
import com.smartdevicelink.proxy.rpc.enums.ButtonPressMode;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.ModuleType;
import com.smartdevicelink.proxy.rpc.enums.Result;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCRequestListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.DebugTool;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

public class SdlService extends Service {

    private static final String TAG = "SDL Service";

    private static final String APP_NAME = "AppServiceConsumer";
    private String APP_ID = "";

    private static final String ICON_FILENAME = "hello_sdl_icon.png";
    private static final String SDL_IMAGE_FILENAME = "sdl_full_image.png";

    private static final String WELCOME_SHOW = "Welcome to AppServiceConsumer";
    private static final String WELCOME_SPEAK = "Welcome to test app services";

    private static final String TEST_COMMAND_NAME = "Test Command";
    private static final int TEST_COMMAND_ID = 1;

    private static final int FOREGROUND_SERVICE_ID = 111;

    // Binder given to clients
    private IBinder mBinder;


    // TCP/IP transport config
    // The default port is 12345
    // The IP is of the machine that is running SDL Core
    private int TCP_PORT = 12345;
    private String DEV_MACHINE_IP_ADDRESS = null;
    private AppHMIType appHMIType = AppHMIType.DEFAULT;

    // variable to create and call functions of the SyncProxy
    private SdlManager sdlManager = null;
    private String serviceID = "";

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        SdlService getService() {
            // Return this instance of SdlService so clients can call public methods
            return SdlService.this;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        mBinder = new LocalBinder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterForeground();
        }
        MainActivity.instance.get().Log("SDL Service::onCreate");
    }

    // Helper method to let the service enter foreground mode
    @SuppressLint("NewApi")
    public void enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Notification serviceNotification = new Notification.Builder(this, channel.getId())
                        .setContentTitle("Connected through SDL")
                        .setSmallIcon(R.drawable.ic_sdl)
                        .build();
                startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MainActivity.instance.get().Log("SDL Service::onStartCommand");
        //startProxy();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        MainActivity.instance.get().Log("SDL Service::onDestroy");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        if (sdlManager != null) {
            sdlManager.dispose();
        }

        super.onDestroy();
    }

    private void setAppID(String appID) {
        APP_ID = appID;
    }

    private String getAppID() {
        return APP_ID;
    }

    private void setIP(String ipAddress) {
        DEV_MACHINE_IP_ADDRESS = ipAddress;
    }


    private final String getIP() {
        return DEV_MACHINE_IP_ADDRESS;
    }

    private void setPort(int ipPort) {
        TCP_PORT = ipPort;
    }

    private void setAppHMIType(AppHMIType type) {
        appHMIType = type;
    }

    private AppHMIType getAppHMIType() {
        return appHMIType;
    }

    public String transportType() {
        return BuildConfig.TRANSPORT;
    }

    public boolean isIPEntered() {
        if (DEV_MACHINE_IP_ADDRESS != null && !DEV_MACHINE_IP_ADDRESS.isEmpty())
            return true;
        return false;
    }

    public final SdlManager getSdlManager() {
        return sdlManager;
    }

    public void startProxy() {
        MainActivity.instance.get().Log("SDL Service::startProxy");

        final String appId = MainActivity.instance.get().getAppID();
        setAppID(appId);

        if (false == MainActivity.instance.get().isAppIDEntered()) {
            MainActivity.instance.get().Log("SDL Service::AppID is not entered !!!");
            MainActivity.instance.get().Log("Restart Application and Enter AppID!!!");
            return;
        }

        final String ipAddress = MainActivity.instance.get().getIPAddress();
        setIP(ipAddress);
        if ("TCP" == transportType() && !isIPEntered()) {
            MainActivity.instance.get().Log("SDL Service:: IP address is not entered !!!");
            MainActivity.instance.get().Log("Restart Application and Enter IP!!!");
            return;
        }

        // This logic is to select the correct transport and security levels defined in the selected build flavor
        // Build flavors are selected by the "build variants" tab typically located in the bottom left of Android Studio
        // Typically in your app, you will only set one of these.
        if (sdlManager == null) {
            Log.i(TAG, "Starting SDL Proxy");
            // Enable DebugTool for debug build type
            if (BuildConfig.DEBUG) {
                DebugTool.enableDebugTool();
            }
            BaseTransportConfig transport = null;
            if (BuildConfig.TRANSPORT.equals("MULTI")) {
                int securityLevel;
                if (BuildConfig.SECURITY.equals("HIGH")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
                } else if (BuildConfig.SECURITY.equals("MED")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
                } else if (BuildConfig.SECURITY.equals("LOW")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
                } else {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
                }
                transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
            } else if (BuildConfig.TRANSPORT.equals("TCP")) {
                transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
            } else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
                MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
                mtc.setRequiresHighBandwidth(true);
                transport = mtc;
            }

            // The app type to be used
            AppHMIType typeToSet = MainActivity.instance.get().getAppHMIType();
            setAppHMIType(typeToSet);
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(typeToSet);

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            // Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
            SdlManagerListener listener = new SdlManagerListener() {
                @Override
                public void onStart() {
                    // HMI Status Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus status = (OnHMIStatus) notification;
                            if (status.getHmiLevel() == HMILevel.HMI_FULL && ((OnHMIStatus) notification).getFirstRun()) {
                                sendCommands();
                                performWelcomeSpeak();
                                performWelcomeShow();
                            }
                        }
                    });

                    // On SYS CAP Updated Notification Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_SYSTEM_CAPABILITY_UPDATED, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            MainActivity.instance.get().Log("System Capabilities Listener is SET");
                            OnSystemCapabilityUpdated command = (OnSystemCapabilityUpdated) notification;
                            try {
                                Log.i(TAG, "ON System Capabilities UPDATED: " + command.serializeJSON().toString());
                                MainActivity.instance.get().Log("OnSystemCapabilityUpdated:\n");
                                MainActivity.instance.get().Log(command.serializeJSON().toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });


                    // Menu Selected Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_COMMAND, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnCommand command = (OnCommand) notification;
                            Integer id = command.getCmdID();
                            if (id != null) {
                                switch (id) {
                                    case TEST_COMMAND_ID:
                                        showTest();
                                        break;
                                }
                            }
                        }
                    });
                }

                @Override
                public void onDestroy() {
                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                }
            };

            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            builder.setTransportType(transport);
            builder.setAppIcon(appIcon);
            sdlManager = builder.build();
            sdlManager.start();
            MainActivity.instance.get().setAllButtonsEnabled(true);
            // Logic related to Services Consumer
            setServicesConsumerListeners();
        }
    }


    private void setServicesConsumerListeners() {
        setOnAppServiceDataNotificationListener();
    }

    private void setServicesProviderListeners() {
        setGASDRequestListener();
        setSendLocationRequestListener();
        setButtoPressRequestListener();
        setPerformAppServicesInteractionRequestListener();
    }


    private void setSendLocationRequestListener() {
        MainActivity.instance.get().Log("SendLocation Request Listener is SET");
        // SendLocation Request Listener
        sdlManager.addOnRPCRequestListener(FunctionID.SEND_LOCATION, new OnRPCRequestListener() {
            @Override
            public void onRequest(RPCRequest request) {
                SendLocation sendLocationRequest = (SendLocation) request;
                try {
                    Log.i(TAG, "SL REQUEST: " + sendLocationRequest.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::Incoming SendLocation Request: " + sendLocationRequest.serializeJSON().toString());

                    // prepare response
                    SendLocationResponse sendLocationResponse = new SendLocationResponse();
                    sendLocationResponse.setSuccess(true);
                    sendLocationResponse.setCorrelationID(sendLocationRequest.getCorrelationID());
                    sendLocationResponse.setResultCode(Result.SUCCESS);

                    sdlManager.sendRPC(sendLocationResponse);
                    Log.i(TAG, "SL RESPONSE: " + sendLocationResponse.serializeJSON().toString());
                    MainActivity.instance.get().Log("SL Response:\n " + sendLocationResponse.serializeJSON().toString());

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setButtoPressRequestListener() {
        MainActivity.instance.get().Log("ButtonPress Request Listener is SET");
        // SendLocation Request Listener
        sdlManager.addOnRPCRequestListener(FunctionID.BUTTON_PRESS, new OnRPCRequestListener() {
            @Override
            public void onRequest(RPCRequest request) {
                ButtonPress buttonPressRequest = (ButtonPress) request;
                try {
                    Log.i(TAG, "BP REQUEST: " + buttonPressRequest.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::Incoming ButtonPress Request: " + buttonPressRequest.serializeJSON().toString());

                    // prepare response
                    ButtonPressResponse buttonPressResponse = new ButtonPressResponse();
                    buttonPressResponse.setSuccess(true);
                    buttonPressResponse.setCorrelationID(buttonPressRequest.getCorrelationID());
                    buttonPressResponse.setResultCode(Result.SUCCESS);

                    sdlManager.sendRPC(buttonPressResponse);
                    Log.i(TAG, "BP RESPONSE: " + buttonPressResponse.serializeJSON().toString());
                    MainActivity.instance.get().Log("BP Response:\n " + buttonPressResponse.serializeJSON().toString());

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void setPerformAppServicesInteractionRequestListener() {
        // Perform App Services Interaction Request Listener
        sdlManager.addOnRPCRequestListener(FunctionID.PERFORM_APP_SERVICES_INTERACTION, new OnRPCRequestListener() {
            @Override
            public void onRequest(RPCRequest request) {
                PerformAppServiceInteraction performAppServiceInteraction = (PerformAppServiceInteraction) request;
                MainActivity.instance.get().Log("Perform App Services Interaction Request Listener is SET");
                try {
                    Log.i(TAG, "PASI REQUEST: " + performAppServiceInteraction.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::Incoming PASI REQUEST:\n " + performAppServiceInteraction.serializeJSON().toString());
                    serviceID = performAppServiceInteraction.getServiceID();
                    Log.i(TAG, "Service ID: " + serviceID);
                    PerformAppServiceInteractionResponse performAppServiceInteractionResponse = new PerformAppServiceInteractionResponse();
                    performAppServiceInteractionResponse.setServiceSpecificResult("ITS LIT");
                    performAppServiceInteractionResponse.setCorrelationID(performAppServiceInteraction.getCorrelationID());
                    performAppServiceInteractionResponse.setInfo("WHO DAT");
                    performAppServiceInteractionResponse.setSuccess(true);
                    performAppServiceInteractionResponse.setResultCode(Result.SUCCESS);
                    sdlManager.sendRPC(performAppServiceInteractionResponse);
                    Log.i(TAG, "PASI RESPONSE: " + performAppServiceInteractionResponse.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::PASI RESPONSE:\n " + performAppServiceInteractionResponse.serializeJSON().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setOnAppServiceDataNotificationListener() {
        // On App Service Data Notification Listener
        MainActivity.instance.get().Log("OASD Notification Listener is SET");
        sdlManager.addOnRPCNotificationListener(FunctionID.ON_APP_SERVICE_DATA, new OnRPCNotificationListener() {
            @Override
            public void onNotified(RPCNotification notification) {
                OnAppServiceData command = (OnAppServiceData) notification;

                try {
                    Log.i(TAG, "OASD NOTIFICATION: " + command.serializeJSON().toString());
                    MainActivity.instance.get().Log("OASD NOTIFICATION:\n " + command.serializeJSON().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setGASDRequestListener() {
        MainActivity.instance.get().Log("GASD Request Listener is SET");
        // Get App Service Data Request Listener
        sdlManager.addOnRPCRequestListener(FunctionID.GET_APP_SERVICE_DATA, new OnRPCRequestListener() {
            @Override
            public void onRequest(RPCRequest request) {
                GetAppServiceData getAppServiceData = (GetAppServiceData) request;

                try {
                    Log.i(TAG, "GASD REQUEST: " + getAppServiceData.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::Incoming GetAppServiceData Request: " + getAppServiceData.serializeJSON().toString());
                    // prepare response
                    Log.i(TAG, "Service ID: " + serviceID);
                    MainActivity.instance.get().Log("Service ID: " + serviceID);

                    Boolean subscribe = getAppServiceData.getSubscribe();

                    MediaServiceData mediaServiceData = new MediaServiceData();
                    mediaServiceData.setMediaTitle("Yaba A DABA DOO");

                    AppServiceData appServiceData = new AppServiceData();
                    appServiceData.setServiceType(AppServiceType.MEDIA.toString());
                    appServiceData.setServiceID(serviceID);
                    appServiceData.setMediaServiceData(mediaServiceData);

                    GetAppServiceDataResponse getAppServiceDataResponse = new GetAppServiceDataResponse();
                    getAppServiceDataResponse.setSuccess(true);
                    getAppServiceDataResponse.setCorrelationID(getAppServiceData.getCorrelationID());
                    getAppServiceDataResponse.setResultCode(Result.SUCCESS);
                    getAppServiceDataResponse.setServiceData(appServiceData);

                    sdlManager.sendRPC(getAppServiceDataResponse);

                    if (subscribe != null && subscribe) {
                        onAppServiceDataNotification();
                    }
                    Log.i(TAG, "GASD RESPONSE: " + getAppServiceDataResponse.serializeJSON().toString());
                    MainActivity.instance.get().Log("GASD Response:\n " + getAppServiceDataResponse.serializeJSON().toString());

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void publishAppServiceRequest() {
        MainActivity.instance.get().Log("SDL Service::publishAppServiceRequest");
        AppServiceManifest asm = new AppServiceManifest();
        asm.setServiceType(AppServiceType.MEDIA.toString());
        asm.setServiceName("MediaServiceProvider");
        asm.setAllowAppConsumers(true);

        PublishAppService pas = new PublishAppService();
        pas.setAppServiceManifest(asm);
        pas.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                MainActivity.instance.get().Log("SDL Service::publishAppServiceResponse:\n");
                try {
                    Log.i(TAG, "PAS RESPONSE : " + response.serializeJSON().toString());
                    MainActivity.instance.get().Log(response.serializeJSON().toString());
                    PublishAppServiceResponse publishAppServiceResponse = (PublishAppServiceResponse) response;
                    AppServiceRecord appServiceRecord = publishAppServiceResponse.getServiceRecord();
                    Log.i(TAG, "App service record: " + appServiceRecord.serializeJSON().toString());
                    serviceID = appServiceRecord.getServiceID();
                    Log.i(TAG, "Service ID: " + serviceID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int correlationId, Result resultCode, String info) {
                Log.i(TAG, "PAS RESPONSE ERROR: " + info + " result code: " + resultCode.toString() + " CID: " + String.valueOf(correlationId));
            }
        });
        sdlManager.sendRPC(pas);
        Log.i(TAG, "SENT PUBLISH APP SERVICE");
        setGASDRequestListener();
        setSendLocationRequestListener();
        setButtoPressRequestListener();
        setPerformAppServicesInteractionRequestListener();
    }

    public void onAppServiceDataNotification() {
        MainActivity.instance.get().Log("SDL Service::onAppServiceDataNotification");
        MediaServiceData mediaServiceData = new MediaServiceData();
        mediaServiceData.setMediaTitle("YABA A DABA DOO REMIX");

        AppServiceData asd = new AppServiceData();
        asd.setServiceID(serviceID);
        asd.setServiceType(AppServiceType.MEDIA.toString());
        asd.setMediaServiceData(mediaServiceData);

        OnAppServiceData onAppServiceData = new OnAppServiceData();
        onAppServiceData.setServiceData(asd);

        try {
            Log.i(TAG, "Sent OASD Notification: " + onAppServiceData.serializeJSON().toString());
            MainActivity.instance.get().Log(onAppServiceData.serializeJSON().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        sdlManager.sendRPC(onAppServiceData);
    }

    private void getFileRequest(String appServiceID, String filename) {
        MainActivity.instance.get().Log("SDL Service::getFileRequest" + "AppServiceID: " + appServiceID + " Filename: " + filename);
        final GetFile getFileRequest = new GetFile();
        getFileRequest.setFileName(filename);
        getFileRequest.setAppServiceId(appServiceID);
        getFileRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                try {
                    Log.i(TAG, "GFR RESPONSE : " + response.serializeJSON().toString());
                    GetFileResponse getFileResponse = (GetFileResponse) response;
                    byte[] fileData = getFileResponse.getBulkData();
                    Log.i(TAG, "GFR RESPONSE : " + String.valueOf(fileData));
                    writeToFile(fileData);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int correlationId, Result resultCode, String info) {
                Log.i(TAG, "GFR RESPONSE ERROR: " + info + " result code: " + resultCode.toString() + " CID: " + String.valueOf(correlationId));
            }
        });

        sdlManager.sendRPC(getFileRequest);
    }

    public void buttonPressRequest() {
        MainActivity.instance.get().Log("SDL Service::buttonPressRequest");
        ButtonPress buttonPress = new ButtonPress();
        buttonPress.setButtonPressMode(ButtonPressMode.LONG);
        buttonPress.setButtonName(ButtonName.PRESET_1);
        buttonPress.setModuleType(ModuleType.AUDIO);
        buttonPress.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {

                try {
                    MainActivity.instance.get().Log("SDL Service::buttonPressResponse:\n" + response.serializeJSON().toString());
                    Log.i(TAG, "BP RESPONSE : " + response.serializeJSON().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int correlationId, Result resultCode, String info) {
                Log.i(TAG, "BP RESPONSE ERROR: " + info + " result code: " + resultCode.toString() + " CID: " + String.valueOf(correlationId));
            }
        });
        sdlManager.sendRPC(buttonPress);
        try {
            Log.i(TAG, "BP REQUEST SENT : " + buttonPress.serializeJSON().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendLocationRequest() {
        MainActivity.instance.get().Log("SDL Service::sendLocationRequest");
        SendLocation sendLocation = new SendLocation();
        // Set test coordinates
        sendLocation.setLatitudeDegrees(38.8951);
        sendLocation.setLongitudeDegrees(-77.0364);

        sendLocation.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                try {
                    Log.i(TAG, "SL RESPONSE : " + response.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::sendLocationResponse:\n" + response.serializeJSON().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int correlationId, Result resultCode, String info) {
                Log.i(TAG, "SL RESPONSE ERROR: " + info + " result code: " + resultCode.toString() + " CID: " + String.valueOf(correlationId));
            }
        });
        sdlManager.sendRPC(sendLocation);
    }

    public void performAppServicesInteraction() {
        MainActivity.instance.get().Log("SDL Service::performAppServicesInteraction Request");
        PerformAppServiceInteraction performAppServiceInteraction = new PerformAppServiceInteraction();
        performAppServiceInteraction.setServiceID(serviceID);
        performAppServiceInteraction.setRequestServiceActive(true);
        performAppServiceInteraction.setOriginApp(getAppID());
        performAppServiceInteraction.setServiceUri("test");
        performAppServiceInteraction.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                MainActivity.instance.get().Log("SDL Service::performAppServicesInteraction Response");
                try {
                    Log.i(TAG, "PASI RESPONSE : " + response.serializeJSON().toString());
                    MainActivity.instance.get().Log(response.serializeJSON().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int correlationId, Result resultCode, String info) {
                Log.i(TAG, "PASI RESPONSE ERROR: " + info + " result code: " + resultCode.toString() + " CID: " + String.valueOf(correlationId));
            }
        });
        sdlManager.sendRPC(performAppServiceInteraction);
    }

    public void getAppServiceDataRequest() {
        MainActivity.instance.get().Log("SDL Service::getAppServiceDataRequest");
        GetAppServiceData getAppServiceData = new GetAppServiceData(AppServiceType.MEDIA.toString());
        getAppServiceData.setSubscribe(true);
        getAppServiceData.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                try {
                    Log.i(TAG, "GASD RESPONSE : " + response.serializeJSON().toString());
                    MainActivity.instance.get().Log("SDL Service::getAppServiceData Response:\n" + response.serializeJSON().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int correlationId, Result resultCode, String info) {
                Log.i(TAG, "GASD RESPONSE ERROR: " + info + " result code: " + resultCode.toString() + " CID: " + String.valueOf(correlationId));
            }
        });;
        sdlManager.sendRPC(getAppServiceData);
    }

    private void writeToFile(byte[] content) {
        try {
            File file = File.createTempFile("somefile", ".json", Environment.getExternalStorageDirectory());
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content);

        } catch (IOException e) {
        }
    }

    /**
     * Add commands for the app on SDL.
     */
    private void sendCommands() {
        MainActivity.instance.get().Log("SDL Service::sendCommands");
        AddCommand command = new AddCommand();
        MenuParams params = new MenuParams();
        params.setMenuName(TEST_COMMAND_NAME);
        command.setCmdID(TEST_COMMAND_ID);
        command.setMenuParams(params);
        command.setVrCommands(Collections.singletonList(TEST_COMMAND_NAME));
        sdlManager.sendRPC(command);
    }

    /**
     * Will speak a sample welcome message
     */
    private void performWelcomeSpeak() {
        MainActivity.instance.get().Log("SDL Service::Speak");
        sdlManager.sendRPC(new Speak(TTSChunkFactory.createSimpleTTSChunks(WELCOME_SPEAK)));
    }

    /**
     * Use the Screen Manager to set the initial screen text and set the image.
     * Because we are setting multiple items, we will call beginTransaction() first,
     * and finish with commit() when we are done.
     */
    private void performWelcomeShow() {
        MainActivity.instance.get().Log("SDL Service::Show");
        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1(APP_NAME);
        sdlManager.getScreenManager().setTextField2(WELCOME_SHOW);
        sdlManager.getScreenManager().setPrimaryGraphic(new SdlArtwork(SDL_IMAGE_FILENAME, FileType.GRAPHIC_PNG, R.drawable.sdl, true));
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    Log.i(TAG, "welcome show successful");
                }
            }
        });
    }

    /**
     * Will show a sample test message on screen as well as speak a sample test message
     */
    private void showTest() {
        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1("Command has been selected");
        sdlManager.getScreenManager().setTextField2("");
        sdlManager.getScreenManager().commit(null);

        sdlManager.sendRPC(new Speak(TTSChunkFactory.createSimpleTTSChunks(TEST_COMMAND_NAME)));
    }


}
