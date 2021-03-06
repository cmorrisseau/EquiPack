package com.example.brentonchasse.myapplication;

import android.app.Activity;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.net.LocalServerSocket;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.net.Uri;
import android.support.v4.widget.DrawerLayout;
import android.os.Handler;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;


public class MainActivity extends Activity implements NavigationDrawerFragment.NavigationDrawerCallbacks,
                                                      BleFragment.OnFragmentInteractionListener,
                                                      WeightFragment.OnFragmentInteractionListener,
                                                      FeedbackFragment.OnFragmentInteractionListener,
                                                      DashboardFragment.OnFragmentInteractionListener,
                                                      SettingsFragment.OnFragmentInteractionListener {
    // Fragment managing the behaviors, interactions and presentation of the navigation drawer.
    private NavigationDrawerFragment mNavigationDrawerFragment;

    BleFragment BleFrag = new BleFragment();
    WeightFragment WeightFrag = new WeightFragment();
    FeedbackFragment FeedbackFrag = new FeedbackFragment();
    DashboardFragment DashboardFrag = new DashboardFragment();
    SettingsFragment SettingsFrag = new SettingsFragment();
    private Fragment currentFrag;
    private String currentFragTag;

    // Used to store the last screen title. For use in {@link #restoreActionBar()}.
    private CharSequence mTitle;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private Handler mHandler;
    private FragmentManager mFragmentManager;

    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattService mService;

    private List<BluetoothGattCharacteristic> mCharacteristicList;
    private BluetoothGattCharacteristic mCharacteristicRead;
    private BluetoothGattCharacteristic mCharacteristicWrite;
    private byte[] mCharacteristicValue;
    private boolean[] mCharacteristicPermissions = new boolean[3];

    private boolean mScanning;

    private int mConnectionState = 0;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;

    public UUID mPrefUUIDService;
    public UUID mPrefUUIDCharacteristicRead;
    public UUID mPrefUUIDCharacteristicWrite;
    public String mDeviceName;
    public String mPrefUUIDServiceString;
    public String mPrefUUIDCharacteristicReadString;
    public String mPrefUUIDCharacteristicWriteString;
    public byte[] mPrefWriteValue;
    public boolean polling = false;
    public int mPrefWeight;

    private final Semaphore available = new Semaphore(1);
    private final Semaphore logLock = new Semaphore(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);
        mHandler = new Handler();
        mBluetoothLeService = new BluetoothLeService();
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mFragmentManager = getFragmentManager();
        mTitle = getTitle();

        // Set up the Navigation drawer.
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));

        //Restore preferences
        SharedPreferences myPreferences = getSharedPreferences("userPrefs", MODE_PRIVATE);
        mDeviceName = myPreferences.getString(
                getString(R.string.settings_device_name_key),
                getString(R.string.app_name));
        mPrefUUIDCharacteristicReadString = myPreferences.getString(
                getString(R.string.settings_UUIDCharacteristicR_key),
                getString(R.string.settings_UUIDCharacteristicR_default));
        mPrefUUIDCharacteristicRead = UUID.fromString(mPrefUUIDCharacteristicReadString);
        mPrefUUIDCharacteristicWriteString = myPreferences.getString(
                getString(R.string.settings_UUIDCharacteristicW_key),
                getString(R.string.settings_UUIDCharacteristicW_default));
        mPrefUUIDCharacteristicWrite = UUID.fromString(mPrefUUIDCharacteristicWriteString);
        mPrefUUIDServiceString = myPreferences.getString(
                getString(R.string.settings_UUIDService_key),
                getString(R.string.settings_UUIDService_default));
        mPrefUUIDService = UUID.fromString(mPrefUUIDServiceString);
        mPrefWeight = myPreferences.getInt(
                getString(R.string.settings_weight_key),
                Integer.parseInt(getString(R.string.settings_weight_default)));
        mPrefWriteValue = hexStringToByteArray(myPreferences.getString(
                getString(R.string.settings_writeValue_key),
                getString(R.string.settings_writeValue_default)));


        //Initialize Bluetooth adapter
        //enableBLEThenScan();
        sendSMS("8603028885", "Hello");
    }

    @Override
    protected void onStop() {
        super.onStop();

        //Store the preferences in case they may have changes
        SharedPreferences myPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = myPreferences.edit();
        editor.putString(getString(R.string.settings_device_name_key), mDeviceName);
        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            scanLeDevice(mBluetoothAdapter.isEnabled());
        }
    }

    @Override
    //TODO: use arrays to store the fragment and tag locations
    public void onNavigationDrawerItemSelected(int position) {
        switch (position) {
            case (0): //EquiPack Dashboard
                currentFrag = DashboardFrag;
                currentFragTag = getString(R.string.app_name);
                pushFragmentOntoStack(mFragmentManager, DashboardFrag, currentFragTag);
                break;
            case 1: //ContentWeightFragment
                currentFrag = WeightFrag;
                currentFragTag = getString(R.string.weight_title);
                pushFragmentOntoStack(mFragmentManager, WeightFrag, currentFragTag);
                break;
            case 2: //StrapOptimizationFragment
                currentFrag = FeedbackFrag;
                currentFragTag = getString(R.string.feedback_title);
                pushFragmentOntoStack(mFragmentManager, FeedbackFrag, currentFragTag);
                break;
            case 3: //BLEFragment
                currentFrag = BleFrag;
                currentFragTag = getString(R.string.ble_title);
                pushFragmentOntoStack(mFragmentManager, BleFrag, currentFragTag);
                break;
            case 4: //SettingsFragment
                currentFrag = SettingsFrag;
                currentFragTag = getString(R.string.settings_title);
                pushFragmentOntoStack(mFragmentManager, SettingsFrag, currentFragTag);
                break;
            default:
                currentFragTag = "DefaultFragmentCase";
                pushFragmentOntoStack(mFragmentManager, DashboardFrag, getString(R.string.app_name));
                break;
        }
    }

    public void pushFragmentOntoStack(FragmentManager fragmentManager,Fragment newFragment,String fragmentName) {
        if (fragmentManager == null)
            fragmentManager = getFragmentManager();

        fragmentManager.beginTransaction()
                .replace(R.id.container, newFragment)
                .addToBackStack(fragmentName)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (mFragmentManager.getBackStackEntryCount() > 1) {
            mFragmentManager.popBackStackImmediate();
        } else {
            mFragmentManager.popBackStackImmediate();
            super.onBackPressed();
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(mTitle);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            restoreActionBar();
            return true;
        }

        return super.onCreateOptionsMenu(menu);
    }

    public void onFragmentClickEvent(View v) {
        if (v == BleFrag.getBLEConnectBtn()) {
            logBle("onFragmentClickEvent: BLE Connection event triggered\n");
            BleFrag.onBleBtnClick(v);
            BleFrag.enableNotify(false);
            BleFrag.enableWrite(false);
            BleFrag.enablePoll(false);
            enableBLEThenScan();
        } else if (v == BleFrag.getBLEWriteBtn()) {
            logBle("onFragmentClickEvent: Writing value event triggered\n");
            setCharacteristic(mCharacteristicWrite, mPrefWriteValue);
        } else if (v == BleFrag.getBLELoopBtn()) {
            byte[] data = hexStringToByteArray("0200000000000000000000000000000000000000");
            polling = !polling;
            if(polling){
                logBle("onFragmentClickEvent: Beginning polling event triggered\n");
                BleFrag.getBLELoopBtn().getBackground().setColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
                setCharacteristic(mCharacteristicWrite, data);
            }
            else if(!polling){
                logBle("onFragmentClickEvent: Stopping polling event triggered\n");
                BleFrag.getBLELoopBtn().getBackground().setColorFilter(0xFF00ff00, PorterDuff.Mode.MULTIPLY);
            }
        } else if (v == BleFrag.getBLENotifyBtn()) {
            logBle("onFragmentClickEvent(): Attempting to enable notifications on \"Read\" characteristic\n");
            setNotifications(mCharacteristicRead, true);
        } else if (v == DashboardFrag.getAddDataBtn()) {
            //int x = DashboardFrag.getXFromInput();
            int y = DashboardFrag.getYFromInput();
            DashboardFrag.addDataPoint(y);

        }
    }

    public void onFragmentInteraction(Uri uri) {}

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * SMS sending related functions
     */
    private void sendSMS(String phoneNumber, String message) {
        String SENT = "SMS_SENT";
        String DELIVERED = "SMS_DELIVERED";

        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(DELIVERED), 0);

        // When the SMS has been sent
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "SMS sent", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(getBaseContext(), "Generic failure", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(getBaseContext(), "No service", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(getBaseContext(), "Null PDU", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(getBaseContext(), "Radio off", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }, new IntentFilter(SENT));

        // When the SMS has been delivered
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "SMS delivered", Toast.LENGTH_SHORT).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getBaseContext(), "SMS not delivered", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));

        //Send the phoneNumber a message, with sent and delivered pending intents
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
    }



    /**
     * BLE related functions
     */

    private void logBle(final String newLog) {
        try {
            logLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            String log = newLog;

            @Override
            public void run() {
                BleFrag.appendText(log);
            }
        });
        logLock.release();
    }

    private void logSet(final String newLog) {
        try {
            logLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            String log = newLog;

            @Override
            public void run() {
                BleFrag.replaceText(log);
            }
        });
        logLock.release();
    }

    public void setDeviceName(String deviceName) {
        mDeviceName = deviceName;
    }

    public void setPrefServiceUUID(String prefUUID) {
        mPrefUUIDService = UUID.fromString(prefUUID);
    }

    public void setPrefReadUUID(String prefUUID) {
        mPrefUUIDCharacteristicRead = UUID.fromString(prefUUID);
    }

    public void setPrefWriteUUID(String prefUUID) {
        mPrefUUIDCharacteristicWrite = UUID.fromString(prefUUID);
    }

    public void setPrefWriteValue(String writePref) {
        mPrefWriteValue = hexStringToByteArray(writePref);
    }

    public void setPrefWeight(int prefWeight) {
        mPrefWeight = prefWeight;
    }

    public void setCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic != null && data != null && mBluetoothGatt != null
                && isCharacteristicWriteable(characteristic)) {

            characteristic.setValue(data);
            boolean writeSent = mBluetoothGatt.writeCharacteristic(characteristic);

            if (writeSent) logBle("setCharacteristic: Asynchronous write has been successfully called.\n");
            else logBle("setCharacteristic: Asynchronous write was NOT successfully called.\n");
        }
    }

    public void setNotifications(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (characteristic != null && mBluetoothGatt != null
                && isCharacterisiticNotifiable(characteristic)) {
            if (enabled) {
                //Enable local notifications
                logBle("setNotifications: Enabling local notifications\n");
                mBluetoothGatt.setCharacteristicNotification(characteristic, true);

                //Enabled remote notifications
                logBle("setNotifications: Enabling remote notifications for descriptor:\n               " +
                        "00002902-0000-1000-8000-00805f9b34fb\n");
                BluetoothGattDescriptor desc = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                logBle("setNotifications: Writing changes made to remote descriptor:\n                " +
                        "00002902-0000-1000-8000-00805f9b34fb\n");
                mBluetoothGatt.writeDescriptor(desc);
            } else {
                //Disable local notifications
                logBle("setNotifications: Disabling local notifications\n");
                mBluetoothGatt.setCharacteristicNotification(characteristic, false);

                //Disable remote notifications
                logBle("setNotifications: Disabling remote notifications for descriptor:\n               " +
                        "00002902-0000-1000-8000-00805f9b34fb\n");
                BluetoothGattDescriptor desc = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                logBle("setNotifications: Writing changes made to remote descriptor:\n                " +
                        "00002902-0000-1000-8000-00805f9b34fb\n");
                mBluetoothGatt.writeDescriptor(desc);
            }

        }
    }

    /**
     * @return Returns true if property is writable
     */
    public boolean isCharacteristicWriteable(BluetoothGattCharacteristic pChar) {
        return (pChar.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    /**
     * @return Returns true if property is Readable
     */
    public boolean isCharacterisitcReadable(BluetoothGattCharacteristic pChar) {
        return ((pChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }

    /**
     * @return Returns true if property is supports notification
     */
    public boolean isCharacterisiticNotifiable(BluetoothGattCharacteristic pChar) {
        int properties = pChar.getProperties();
        return (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }

    public void enableBLEThenScan() {
        final BluetoothManager bluetoothManager
                = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        //Enable Bluetooth
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            logBle("Requesting user to turn BLE ON\n");
        } else if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            logBle("scanLeDevice(): called\n");
            scanLeDevice(mBluetoothAdapter.isEnabled());
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanning) logBle("scanLeDevice(): No correct " +
                            "device was found -> scan stopping\n");
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            logBle("scanLeDevice(): scan started\n");
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            String deviceName = device.getName();
            if (deviceName != null && deviceName.equals(mDeviceName) && mScanning) {
                try {
                    available.acquire();
                    mScanning = false;
                    //mBluetoothAdapter.stopLeScan(mLeScanCallback);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        logBle("mLeScanCallback: Preferred Device Found!\n");
                        logBle("mLeScanCallback: CONNECTING to device's GATT server\n");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mBluetoothGatt = device.connectGatt(mBluetoothLeService, false, mBluetoothLeService.mGattCallback);
                        logBle("scanLeDevice(): scan stopping\n");
                    }
                });
                available.release();
            }
        }
    };

    private class BluetoothLeService extends Service {
        final protected char[] hexArray = "0123456789ABCDEF".toCharArray();
        private final String TAG = BluetoothLeService.class.getSimpleName();
        private static final int STATE_DISCONNECTED = 0;
        private static final int STATE_CONNECTED = 2;


        public final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {


            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    logBle("onConnectionStateChange(): Connected to GATT server\n");
                    mConnectionState = STATE_CONNECTED;
                    mBluetoothGatt.discoverServices();
                    Log.i(TAG, "Connected to GATT server.");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mConnectionState = STATE_DISCONNECTED;
                    logBle("onConnectionStateChange(): Disconnected from GATT server\n");
                    Log.i(TAG, "Disconnected from GATT server.");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    logBle("onServicesDiscovered(): attempting to get preferred service: "
                            + mPrefUUIDService.toString()
                            + "\n");

                    mService = gatt.getService(mPrefUUIDService);
                    logBle("onServicesDiscovered(): preferred service verified and received\n");
                    Log.i(TAG, "Status onServiceDiscovered: " + status);

                    //Perform a deeper investigation of the preferred service
                    if (mService != null) {
                        logBle("onServicesDiscovered(): Getting \"Read\" and \"Write\" characteristics of service: "
                                + mPrefUUIDServiceString + "\n"
                                + "          Read UUID: " + mPrefUUIDCharacteristicReadString + "\n"
                                + "         Write UUID: " + mPrefUUIDCharacteristicWriteString + "\n");
                        mCharacteristicRead = mService.getCharacteristic(mPrefUUIDCharacteristicRead);
                        mCharacteristicWrite = mService.getCharacteristic(mPrefUUIDCharacteristicWrite);

                        checkCharacteristicProperties(mCharacteristicRead, "Read");
                        checkCharacteristicProperties(mCharacteristicWrite, "Write");

                        //Read the preferred characteristic of the preferred service
                        if (mCharacteristicRead != null && isCharacterisitcReadable(mCharacteristicRead)) {
                            try {
                                logBle("onServicesDiscovered(): Reading \"Read\" characteristic\n");
                                mBluetoothGatt.readCharacteristic(mCharacteristicRead);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (mCharacteristicWrite != null && isCharacterisitcReadable(mCharacteristicWrite)) {
                            try {
                                logBle("onServicesDiscovered(): Reading \"Write\" characteristic\n");
                                mBluetoothGatt.readCharacteristic(mCharacteristicWrite);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        logBle("onServicesDiscovered(): Service UUID does not match a valid UUID for this device. Please change this UUID in app preferences.");
                    }

                    Log.i(TAG, "Status onServiceDiscovered: " + status);

                } else {
                    Log.i(TAG, "onServicesDiscovered received: " + status);
                }
            }

            @Override
            // Result of a characteristic read operation
            public void onCharacteristicRead(BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mCharacteristicValue = characteristic.getValue();
                    logBle("onCharacteristicRead(): Successfully read Characteristic:\n                       "
                            + characteristic.getUuid().toString() + "\n" + "                       Value:  0x"
                            + bytesToHex(mCharacteristicValue) + "\n");

                    BleFrag.enableNotify(true);
                }
            }

            public void checkCharacteristicProperties(BluetoothGattCharacteristic characteristic, String name){
                if(characteristic == null) {
                    logBle("checkCharacteristicProperties(): not a valid characteristic UUID for: " + name
                            + "\n                       Change in preferences.\n");
                    return;
                }
                logBle("checkCharacteristicProperties(): Checking " + "\"" + name + "\"" + " characteristic Properties...\n");

                if (isCharacteristicWriteable(characteristic)) {
                    logBle("checkCharacteristicProperties():       Writeable -> true\n");
                    mCharacteristicPermissions[0] = true;
                } else {
                    logBle("checkCharacteristicProperties():       Writeable -> false\n");
                    mCharacteristicPermissions[0] = false;
                }
                if (isCharacterisitcReadable(characteristic)) {
                    logBle("checkCharacteristicProperties():       Readable -> true\n");
                    mCharacteristicPermissions[1] = true;
                } else {
                    logBle("checkCharacteristicProperties():       Readable -> false\n");
                    mCharacteristicPermissions[1] = false;
                }
                if (isCharacterisiticNotifiable(characteristic)) {
                    logBle("checkCharacteristicProperties():       Notifiable -> true\n");
                    mCharacteristicPermissions[2] = true;
                } else {
                    logBle("checkCharacteristicProperties():       Notifiable -> false\n");
                    mCharacteristicPermissions[2] = false;
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
                logBle("onDescriptorWrite:\n                    Descriptor UUID: " + descriptor.getUuid() +
                        "\n                    Has a new value of: "
                        + bytesToHex(descriptor.getValue()) + "\n");
                BleFrag.enableWrite(true);
                BleFrag.enablePoll(true);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                String stringVal = new String(characteristic.getValue());
                byte[] byteVal= characteristic.getValue();
                String hexVal = bytesToHex(byteVal);

                double sensor1 = ( (double)( ( ( (int)byteVal[1] & 0xFF ) * 256 ) + ( (int)byteVal[2] & 0xFF ) ) * 3.3f ) / 4096f;
                double sensor2 = ( (double)( ( ( (int)byteVal[3] & 0xFF ) * 256 ) + ( (int)byteVal[4] & 0xFF ) ) * 3.3f ) / 4096f;

                if(hexVal.charAt(0) == 'A') {
                    if (hexVal.charAt(1) == '1') {
                        logBle("onCharacteristicChanged:\n                   Characteristic UUID: " + characteristic.getUuid()
                                + "\n                   Return OpCode: 0xA1 == Test"
                                + "\n                   Return Data: "
                                + hexVal.substring(2, hexVal.length()) + "\n");
                    } else if (hexVal.charAt(1) == '2') {
                        logSet("onCharacteristicChanged:\n                   Characteristic UUID: " + characteristic.getUuid()
                                + "\n                   Return OpCode: 0xA2 == Read ADCs"
                                + "\n                   Return Data:"
                                + "\n                           Sensor 1: " + sensor1 + "V"
                                + "\n                           Sensor 2: " + sensor2 + "V\n");
                                //+ hexVal.substring(2, hexVal.length()) + "\n");
                        if(polling) {
                            byte[] data = hexStringToByteArray("0200000000000000000000000000000000000000");
                            setCharacteristic(mCharacteristicWrite, data);
                        }
                    }
                }
            }

            public String bytesToHex(byte[] bytes) {
                char[] hexChars = new char[bytes.length * 2];
                for (int j = 0; j < bytes.length; j++) {
                    int v = bytes[j] & 0xFF;
                    hexChars[j * 2] = hexArray[v >>> 4];
                    hexChars[j * 2 + 1] = hexArray[v & 0x0F];
                }
                return new String(hexChars);
            }

        };

        //Necessary for implementing a Service
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
