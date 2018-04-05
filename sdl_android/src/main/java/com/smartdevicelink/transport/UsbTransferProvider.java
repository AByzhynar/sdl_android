package com.smartdevicelink.transport;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.smartdevicelink.util.AndroidTools;

import java.lang.ref.WeakReference;

public class UsbTransferProvider {
    private static final String TAG = "UsbTransferProvider";
    public static final int SENDING_PFD_ID = 5555;
	public static final int  RCVING_PFD_ID = 5556;
	public static final String BIND_REQUEST_TYPE_USB_PFD = "BIND_REQUEST_TYPE_USB_PFD";

	private Context context = null;
    private boolean isBound = false;
    Messenger routerServiceMessenger = null;
    private ComponentName routerService = null;
    private int flags = 0;

    final Messenger clientMessenger;

    ParcelFileDescriptor usbPfd;
	final UsbTransferCallback callback;

    private ServiceConnection routerConnection= new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Bound to service " + className.toString());
            routerServiceMessenger = new Messenger(service);
            isBound = true;
            //So we just established our connection
            //Register with router service
            Message msg = Message.obtain();
            msg.what = 5555;
            msg.arg1 = flags;
            msg.replyTo = clientMessenger;
            msg.obj = usbPfd;
            try {
                routerServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "UN-Bound from service " + className.getClassName());
            routerServiceMessenger = null;
            isBound = false;
        }
    };

    public UsbTransferProvider(Context context, ComponentName service, UsbAccessory usbAccessory, UsbTransferCallback callback){
        if(context == null || service == null || usbAccessory == null){
            throw new IllegalStateException("Supplied params are not correct. Context == null? "+ (context==null) + " ComponentName == null? " + (service == null) + " Usb Accessory == null? " + usbAccessory);
        }
        this.context = context;
        this.routerService = service;
        this.clientMessenger = new Messenger(new ClientHandler(this));
        usbPfd = getFileDescriptor(usbAccessory);
        this.callback = callback;
        if(usbPfd != null) {
	        checkIsConnected();
        }
    }

    @SuppressLint("NewApi")
    private ParcelFileDescriptor getFileDescriptor(UsbAccessory accessory){
         UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
         if(manager != null){
             return manager.openAccessory(accessory);
         }
         return  null;
    }

    public void setFlags(int flags){
        this.flags = flags;
    }

    public void checkIsConnected(){
        if(!AndroidTools.isServiceExported(context,routerService) || !bindToService()){
            //We are unable to bind to service
            Log.e(TAG, "Unable to bind to service");
            unBindFromService();
        }
    }

    public void cancel(){
        if(isBound){
            unBindFromService();
        }
    }

    private boolean bindToService(){
        if(isBound){
            return true;
        }
        if(clientMessenger == null){
            return false;
        }
        Intent bindingIntent = new Intent();
        bindingIntent.setClassName(this.routerService.getPackageName(), this.routerService.getClassName());//This sets an explicit intent
        //Quickly make sure it's just up and running
        context.startService(bindingIntent);
        bindingIntent.setAction(BIND_REQUEST_TYPE_USB_PFD);
        return context.bindService(bindingIntent, routerConnection, Context.BIND_AUTO_CREATE);
    }

    private void unBindFromService(){
        try{
            if(context!=null && routerConnection!=null){
                context.unbindService(routerConnection);
            }else{
                Log.w(TAG, "Unable to unbind from router service, context was null");
            }

        }catch(IllegalArgumentException e){
            //This is ok
        }
    }

    private void finish(){
        unBindFromService();
        routerServiceMessenger =null;
    }

	private void handleUsbTransferResponse(boolean success){
		if(callback != null){
			callback.onUsbTransferUpdate(success);
		}
	}

    static class ClientHandler extends Handler {
        final WeakReference<UsbTransferProvider> provider;

        public ClientHandler(UsbTransferProvider provider){
            super(Looper.getMainLooper());
            this.provider = new WeakReference<UsbTransferProvider>(provider);
        }

        @Override
        public void handleMessage(Message msg) {
            if(provider.get()==null){
                return;
            }

            switch (msg.what) {
                case 5556: //TransportConstants.USB_ACC_RECEIVED:
                    Log.d(TAG, "Successful USB transfer");
                    provider.get().handleUsbTransferResponse(true);
                    provider.get().finish();
                    break;
                default:
	                provider.get().handleUsbTransferResponse(false);
                    break;
            }
        }
    };

	public interface UsbTransferCallback{
		public void onUsbTransferUpdate(boolean success);
	}


}
