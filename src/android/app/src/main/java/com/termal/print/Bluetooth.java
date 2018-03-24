package cordova.plugins.minithermalprinter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import sdk.Command;
import sdk.PrintPicture;
import sdk.PrinterCommand;
import zj.com.customize.sdk.Other;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cordova.PluginResult;

import android.R;

public class Bluetooth extends CordovaPlugin {

    private int timeout = 10000;
    private CallbackContext callbackContext;
    public Context context;
    private String SerialNo = "";
    private String isoTemplate = "";
    private boolean isMatched = false;
    private boolean isCapRunning = false;
    /******************************************************************************************************/
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the services
    private BluetoothService mService = null;
    /******************************************************************************************************/
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;
    public static final int MESSAGE_UNABLE_CONNECT = 7;
    /*******************************************************************************************************/
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CHOSE_BMP = 3;
    private static final int REQUEST_CAMER = 4;
    /*******************************************************************************************************/
    private static final String CHINESE = "GBK";
    private static final String THAI = "CP874";
    private static final String KOREAN = "EUC-KR";
    private static final String BIG5 = "BIG5";
    private JSONArray Printtxt = null;
    private String RemoteAddress = "";

    public Bluetooth() {

    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        try {
            this.callbackContext = callbackContext;
            context = this.cordova.getActivity().getApplicationContext();
            Printtxt = data;
            if (action.equals("Print")) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!RemoteAddress.equals("") && mService.getState() == BluetoothService.STATE_CONNECTED) {
                     Print_Test();
                    //Print_Ex();
                } else if (!RemoteAddress.equals("") && mService.getState() != BluetoothService.STATE_CONNECTED && BluetoothAdapter.checkBluetoothAddress(RemoteAddress)) {
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(RemoteAddress);
                    // Attempt to connect to the device
                    mService.connect(device);

                } else if (mBluetoothAdapter == null) {
                    onFailedRes("No Bluetooth radio found in your device.");
                    return true;
                } else {
                    if (mBluetoothAdapter.isEnabled()) {
                        ScanBluetoothDevice();
                    } else if (mBluetoothAdapter.getProfileConnectionState(0) == 1) {

                    } else {
                        EnableBlueToothRadio();
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            return true;
        }
    }

    @Override
    public void onDestroy() {
        try {
            super.onDestroy();

        } catch (Exception ex) {
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            switch (requestCode) {
                case REQUEST_ENABLE_BT: {
                    // When the request to enable Bluetooth returns
                    if (resultCode == Activity.RESULT_OK) {
                        // Bluetooth is now enabled, so set up a session
                        mService = new BluetoothService(context, mHandler);
                        ScanBluetoothDevice();

                    } else {
                        // User did not enable Bluetooth or an error occured
                        Toast.makeText(context, "Bluetooth does not start", Toast.LENGTH_SHORT).show();
                        onFailedRes("Bluetooth does not start");
                    }
                    break;
                }
                case REQUEST_CONNECT_DEVICE: {
                    // When DeviceListActivity returns with a device to connect
                    if (resultCode == Activity.RESULT_OK) {
                        // Get the device MAC address
                        RemoteAddress = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                        // Get the BLuetoothDevice object
                        if (BluetoothAdapter.checkBluetoothAddress(RemoteAddress)) {
                            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(RemoteAddress);
                            // Attempt to connect to the device
                            mService = new BluetoothService(context, mHandler);
                            mService.connect(device);
                        }
                    }
                    break;
                }


            }
        } catch (Exception ex) {
            onFailedRes("Unable to process (103)");
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:

                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show();
                            Print_Test();
                            //Print_Ex();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            Toast.makeText(context, "Connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            Toast.makeText(context, "Not Connected", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case MESSAGE_WRITE:

                    break;
                case MESSAGE_READ:

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(context, "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(context, msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_CONNECTION_LOST:
                    Toast.makeText(context, "Device connection was lost", Toast.LENGTH_SHORT).show();

                    break;
                case MESSAGE_UNABLE_CONNECT:
                    Toast.makeText(context, "Unable to connect device", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void EnableBlueToothRadio() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.cordova.startActivityForResult((CordovaPlugin) this, enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the session
        }
    }

    private void ScanBluetoothDevice() {
        Intent serverIntent = new Intent(context, DeviceListActivity.class);//********* doubt
        this.cordova.startActivityForResult((CordovaPlugin) this, serverIntent, REQUEST_CONNECT_DEVICE);
    }

    private void Print_Ex() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/ HH:mm:ss ");
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        String str = formatter.format(curDate);
        String date = str + "\n\n\n\n\n\n";

        try {
            byte[] qrcode = PrinterCommand.getBarCommand("Zijiang Electronic Thermal Receipt Printer!", 0, 3, 6);//
            Command.ESC_Align[2] = 0x01;
            SendDataByte(Command.ESC_Align);
            SendDataByte(qrcode);

            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x11;
            SendDataByte(Command.GS_ExclamationMark);
            SendDataByte("NIKE Shop\n".getBytes("GBK"));
            Command.ESC_Align[2] = 0x00;
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x00;
            SendDataByte(Command.GS_ExclamationMark);
            SendDataByte("Number:  888888\nReceipt  S00003333\nCashier：1001\nDate：xxxx-xx-xx\nPrint Time：xxxx-xx-xx  xx:xx:xx\n".getBytes("GBK"));
            SendDataByte("Name    Quantity    price  Money\nShoes   10.00       899     8990\nBall    10.00       1599    15990\n".getBytes("GBK"));
            SendDataByte("Quantity：             20.00\ntotal：                16889.00\npayment：              17000.00\nKeep the change：      111.00\n".getBytes("GBK"));
            SendDataByte("company name：NIKE\nSite：www.xxx.xxx\naddress：ShenzhenxxAreaxxnumber\nphone number：0755-11111111\nHelpline：400-xxx-xxxx\n================================\n".getBytes("GBK"));
            Command.ESC_Align[2] = 0x01;
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x11;
            SendDataByte(Command.GS_ExclamationMark);
            SendDataByte("Welcome again!\n".getBytes("GBK"));
            Command.ESC_Align[2] = 0x00;
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x00;
            SendDataByte(Command.GS_ExclamationMark);

            SendDataByte("(The above information is for testing template, if agree, is purely coincidental!)\n".getBytes("GBK"));
            Command.ESC_Align[2] = 0x02;
            SendDataByte(Command.ESC_Align);
            SendDataString(date);
            SendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(48));
            SendDataByte(Command.GS_V_m_n);
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private void SendDataString(String data) {
        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(context, "Please connect a Bluetooth printer", Toast.LENGTH_SHORT).show();
            return;
        }
        if (data.length() > 0) {
            try {
                mService.write(data.getBytes("GBK"));
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void Print_Test() {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/ HH:mm:ss ");
            Date curDate = new Date(System.currentTimeMillis());//获取当前时间
            String str = formatter.format(curDate);
            String date = str + "\n";

            String[] MerchantDetails = (Printtxt.getString(0).toString()).split("\\^");
            byte[] qrcode = PrinterCommand.getBarCommand("Electronic Thermal Receipt Printer!", 0, 3, 6);//
            Command.ESC_Align[2] = 0x01;
            SendDataByte(Command.ESC_Align);
            SendDataByte(qrcode);

            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x11;
            SendDataByte(Command.GS_ExclamationMark);
            SendDataByte((MerchantDetails[0].toString()).getBytes("GBK"));
            Command.ESC_Align[2] = 0x00;
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x00;
            SendDataByte(Command.GS_ExclamationMark);

            SendDataByte(PrinterCommand.POS_Print_Text("Customer Info\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(PrinterCommand.POS_Print_Text("━━━━━━━━━━━━━━━━\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(PrinterCommand.POS_Print_Text(Printtxt.getString(1) + "\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x11;
            SendDataByte(Command.GS_ExclamationMark);

            SendDataByte(PrinterCommand.POS_Print_Text("Order Info\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(PrinterCommand.POS_Print_Text("━━━━━━━━━━━━━━━━\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(PrinterCommand.POS_Print_Text(Printtxt.getString(2).replaceAll("&#36;","") + "\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x11;
            SendDataByte(Command.GS_ExclamationMark);

            SendDataByte(PrinterCommand.POS_Print_Text("Payment Info\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(PrinterCommand.POS_Print_Text("━━━━━━━━━━━━━━━━\n", CHINESE, 0, 0, 0, 0));
            SendDataByte(PrinterCommand.POS_Print_Text(Printtxt.getString(3) + "\n\n", CHINESE, 0, 0, 0, 0));

            Command.ESC_Align[2] = 0x01;
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x11;
            SendDataByte(Command.GS_ExclamationMark);
            SendDataByte(PrinterCommand.POS_Print_Text("━━━━━━━━━━━━━━━━\n", CHINESE, 0, 0, 0, 0));
            SendDataByte("Welcome again!\n".getBytes("GBK"));

            Command.ESC_Align[2] = 0x00;
            SendDataByte(Command.ESC_Align);
            Command.GS_ExclamationMark[2] = 0x00;
            SendDataByte(Command.GS_ExclamationMark);

            SendDataByte((MerchantDetails[0].toString() + "\n").getBytes("GBK"));
            SendDataByte((MerchantDetails[1].toString() + "\n").getBytes("GBK"));
            SendDataByte((MerchantDetails[2].toString() + "\n").getBytes("GBK"));
            Command.ESC_Align[2] = 0x02;
            SendDataByte(Command.ESC_Align);
            SendDataString(date);
            SendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(32));
            SendDataByte(Command.GS_V_m_n);
            JSONObject object = new JSONObject();
            object.put("errorcode", 0);
            object.put("errormsg", "Print Success");
            onSuccessRes(object);
        } catch (Exception ex) {

        }

    }

    private void SendDataByte(byte[] data) {
        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(context, "Please connect a Bluetooth printer", Toast.LENGTH_SHORT).show();
            ScanBluetoothDevice();
            return;
        }
        mService.write(data);
    }

    private void onSuccessRes(JSONObject response) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, response);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        }
    }

    private void onFailedRes(String error) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, error);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        }
    }


}
